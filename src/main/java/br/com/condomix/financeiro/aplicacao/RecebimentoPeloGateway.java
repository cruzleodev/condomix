package br.com.condomix.financeiro.aplicacao;

import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.pagamentos.TipoDeGateway;
import br.com.condomix.financeiro.dominio.Boleto;
import br.com.condomix.financeiro.dominio.Cobranca;
import br.com.condomix.financeiro.dominio.FormaDePagamento;
import br.com.condomix.financeiro.dominio.SituacaoDoBoleto;
import br.com.condomix.financeiro.dominio.StatusCobranca;
import br.com.condomix.pagamentos.GatewayDePagamento.FormaDoPagamento;
import br.com.condomix.pagamentos.GatewayDePagamento.PagamentoConfirmado;
import br.com.condomix.financeiro.persistencia.BoletoRepository;
import br.com.condomix.financeiro.persistencia.CobrancaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Caso de uso: baixa automática. O gateway avisa (webhook) que um boleto foi pago; o Condomix confere o
 * pagamento na API do gateway (não confia no corpo do aviso), dá baixa na cobrança e o caixa recebe o valor
 * e a tarifa. Avisos repetidos não dão baixa duas vezes.
 */
@Service
public class RecebimentoPeloGateway {

	private static final Logger log = LoggerFactory.getLogger(RecebimentoPeloGateway.class);

	private static final Map<FormaDoPagamento, FormaDePagamento> FORMAS = Map.of(
			FormaDoPagamento.BOLETO, FormaDePagamento.BOLETO,
			FormaDoPagamento.PIX, FormaDePagamento.PIX,
			FormaDoPagamento.OUTRA, FormaDePagamento.OUTRO);

	private final ContaDoGateway contas;

	private final BoletoRepository boletos;

	private final CobrancaRepository cobrancas;

	private final BaixaDeCobrancas baixa;

	private final TransactionTemplate transacao;

	public RecebimentoPeloGateway(ContaDoGateway contas, BoletoRepository boletos, CobrancaRepository cobrancas,
			BaixaDeCobrancas baixa, TransactionTemplate transacao) {
		this.contas = contas;
		this.boletos = boletos;
		this.cobrancas = cobrancas;
		this.baixa = baixa;
		this.transacao = transacao;
	}

	public enum Resultado {
		/** A cobrança foi baixada agora. */
		BAIXADA,
		/** Aviso repetido: já estava baixada por este boleto. */
		JA_BAIXADA,
		/** O gateway ainda não confirma o pagamento (ex.: aviso de outro evento). */
		NAO_PAGO,
		/** Boleto desconhecido neste condomínio. */
		IGNORADO,
		/** O morador pagou um boleto de cobrança já paga por fora ou cancelada: o síndico precisa devolver. */
		PAGO_EM_DUPLICIDADE
	}

	/** O aviso tem o token que o gateway manda? (Comparação em tempo constante.) */
	public boolean avisoAutentico(UUID condominioId, String token) {
		return TenantAtual.executarComo(condominioId, () -> contas.avisoConfere(token));
	}

	public Resultado confirmar(UUID condominioId, TipoDeGateway tipo, String idNoGateway) {
		return TenantAtual.executarComo(condominioId, () -> confirmarNoCondominioAtual(tipo, idNoGateway));
	}

	private Resultado confirmarNoCondominioAtual(TipoDeGateway tipo, String idNoGateway) {
		var acesso = contas.configurada().filter(a -> a.gateway().tipo() == tipo).orElse(null);
		Boleto boleto = transacao.execute(s -> boletos.findByGatewayAndIdNoGateway(tipo, idNoGateway).orElse(null));
		if (acesso == null || boleto == null) {
			return Resultado.IGNORADO;
		}
		if (boleto.getSituacao() == SituacaoDoBoleto.PAGO) {
			return Resultado.JA_BAIXADA;
		}
		PagamentoConfirmado pagamento = acesso.gateway().pagamento(acesso.credencial(), idNoGateway).orElse(null);
		if (pagamento == null) {
			return Resultado.NAO_PAGO;
		}
		return transacao.execute(s -> baixar(boleto.getId(), pagamento));
	}

	private Resultado baixar(UUID boletoId, PagamentoConfirmado pagamento) {
		Boleto boleto = boletos.findById(boletoId).orElseThrow();
		if (boleto.getSituacao() == SituacaoDoBoleto.PAGO) {
			return Resultado.JA_BAIXADA; // outro aviso chegou primeiro
		}
		Cobranca cobranca = cobrancas.findById(boleto.getCobrancaId()).orElseThrow();
		boleto.pago(pagamento.tarifa());
		if (cobranca.getStatus() != StatusCobranca.ABERTA) {
			log.warn("Boleto {} pago no gateway, mas a cobrança {} está {}: pagamento em duplicidade",
					boleto.getId(), cobranca.getId(), cobranca.getStatus());
			return Resultado.PAGO_EM_DUPLICIDADE;
		}
		baixa.registrarPagamentoPeloGateway(cobranca.getId(), pagamento.data(), pagamento.valorPago(), pagamento.tarifa(),
				FORMAS.get(pagamento.forma()), "Pago pelo " + (pagamento.forma() == FormaDoPagamento.PIX ? "Pix" : "boleto")
						+ " (baixa automática)");
		return Resultado.BAIXADA;
	}

}
