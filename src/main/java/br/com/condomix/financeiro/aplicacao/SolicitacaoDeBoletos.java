package br.com.condomix.financeiro.aplicacao;

import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.financeiro.dominio.Boleto;
import br.com.condomix.financeiro.dominio.CobrancaCancelada;
import br.com.condomix.financeiro.dominio.CobrancasEmitidas;
import br.com.condomix.financeiro.dominio.ContaNoGateway;
import br.com.condomix.financeiro.dominio.OrigemCobranca;
import br.com.condomix.financeiro.dominio.PagamentoDeCobrancaRegistrado;
import br.com.condomix.financeiro.dominio.SituacaoDoBoleto;
import br.com.condomix.financeiro.dominio.StatusCobranca;
import br.com.condomix.financeiro.persistencia.BoletoRepository;
import br.com.condomix.financeiro.persistencia.CobrancaRepository;
import br.com.condomix.financeiro.persistencia.ContaNoGatewayRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Decide quais cobranças precisam de boleto e quais boletos devem ser cancelados. Só grava o pedido
 * (na mesma transação da cobrança); quem fala com o gateway é o {@link ProcessadorDeBoletos}.
 */
@Service
@Transactional
public class SolicitacaoDeBoletos {

	private final BoletoRepository boletos;

	private final CobrancaRepository cobrancas;

	private final ContaNoGatewayRepository contas;

	public SolicitacaoDeBoletos(BoletoRepository boletos, CobrancaRepository cobrancas, ContaNoGatewayRepository contas) {
		this.boletos = boletos;
		this.cobrancas = cobrancas;
		this.contas = contas;
	}

	/** Taxa do mês ou avulsa: com o gateway ligado, cada cobrança ganha o seu boleto. */
	@EventListener
	void cobrancasEmitidas(CobrancasEmitidas evento) {
		contaAtiva().ifPresent(conta -> evento.cobrancaIds().forEach(id -> {
			if (boletos.vigente(id).isEmpty()) {
				boletos.save(new Boleto(TenantAtual.obrigatorio(), id, conta.getGateway()));
			}
		}));
	}

	@EventListener
	void cobrancaCancelada(CobrancaCancelada evento) {
		boletos.vigente(evento.cobrancaId()).ifPresent(Boleto::pedirCancelamento);
	}

	/** Pago por fora (dinheiro, transferência): o boleto é cancelado para o morador não pagar duas vezes. */
	@EventListener
	void pagamentoRegistrado(PagamentoDeCobrancaRegistrado evento) {
		if (!evento.peloGateway()) {
			boletos.vigente(evento.cobrancaId()).ifPresent(Boleto::pedirCancelamento);
		}
	}

	/**
	 * Para as cobranças em aberto que ainda não têm boleto (ex.: criadas antes de ligar o gateway, ou com o pagamento
	 * estornado) e as que falharam. As importadas ficam de fora: a administradora já emitiu o boleto delas.
	 *
	 * @return quantos boletos foram pedidos
	 */
	public int emitirOsQueFaltam() {
		ContaNoGateway conta = contaAtiva().orElseThrow(() -> RegraDeNegocioException.conflito(
				"O boleto e o Pix ainda não estão ligados para este condomínio. Fale com o suporte do Condomix."));
		int pedidos = 0;
		for (var cobranca : cobrancas.findByStatusAndOrigemNot(StatusCobranca.ABERTA, OrigemCobranca.IMPORTACAO)) {
			Optional<Boleto> vigente = boletos.vigente(cobranca.getId());
			if (vigente.isEmpty()) {
				boletos.save(new Boleto(TenantAtual.obrigatorio(), cobranca.getId(), conta.getGateway()));
				pedidos++;
			}
			else if (vigente.get().getSituacao() == SituacaoDoBoleto.FALHOU) {
				vigente.get().tentarDeNovo();
				pedidos++;
			}
		}
		return pedidos;
	}

	/** O síndico corrigiu o motivo da falha (ex.: cadastrou o CPF do responsável). */
	public void tentarDeNovo(UUID cobrancaId) {
		Boleto boleto = boletos.vigente(cobrancaId)
				.filter(b -> b.getSituacao() == SituacaoDoBoleto.FALHOU)
				.orElseThrow(() -> new NaoEncontradoException("Esta cobrança não tem boleto com falha"));
		boleto.tentarDeNovo();
	}

	private Optional<ContaNoGateway> contaAtiva() {
		return contas.doCondominio().filter(ContaNoGateway::isAtiva);
	}

}
