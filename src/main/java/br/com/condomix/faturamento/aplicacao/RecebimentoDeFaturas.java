package br.com.condomix.faturamento.aplicacao;

import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.faturamento.SituacaoDaFatura;
import br.com.condomix.faturamento.dominio.Fatura;
import br.com.condomix.faturamento.persistencia.FaturaRepository;
import br.com.condomix.pagamentos.GatewayDePagamento.PagamentoConfirmado;
import br.com.condomix.pagamentos.TipoDeGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Baixa automática da fatura: o gateway avisa (webhook) que um boleto foi pago; o Condomix confere o pagamento
 * na API do gateway (não confia no corpo do aviso) e marca a fatura como paga. Se o acesso estava suspenso por
 * esta dívida, ele volta. Avisos repetidos não fazem nada.
 */
@Service
public class RecebimentoDeFaturas {

	private static final Logger log = LoggerFactory.getLogger(RecebimentoDeFaturas.class);

	private final ContaDeRecebimentoService conta;

	private final FaturaRepository faturas;

	private final ReativacaoDoAcesso reativacao;

	private final TransactionTemplate transacao;

	RecebimentoDeFaturas(ContaDeRecebimentoService conta, FaturaRepository faturas, ReativacaoDoAcesso reativacao,
			TransactionTemplate transacao) {
		this.conta = conta;
		this.faturas = faturas;
		this.reativacao = reativacao;
		this.transacao = transacao;
	}

	public enum Resultado {
		/** A fatura foi paga agora. */
		PAGA,
		/** Aviso repetido. */
		JA_PAGA,
		/** O gateway ainda não confirma o pagamento. */
		NAO_PAGA,
		/** Boleto desconhecido (de outro sistema na mesma conta, por exemplo). */
		IGNORADO,
		/** Pagaram um boleto de fatura já cancelada: o Condomix precisa devolver. */
		PAGA_EM_DUPLICIDADE
	}

	/** O aviso tem o token da conta do Condomix? (Comparação em tempo constante.) */
	public boolean avisoAutentico(String token) {
		return conta.avisoConfere(token);
	}

	public Resultado confirmar(TipoDeGateway tipo, String idNoGateway) {
		var acesso = conta.configurada().filter(a -> a.gateway().tipo() == tipo).orElse(null);
		Fatura encontrada = TenantAtual.comoSistema(() -> transacao.execute(s -> faturas.findByGatewayAndIdNoGateway(tipo, idNoGateway).orElse(null)));
		if (acesso == null || encontrada == null) {
			return Resultado.IGNORADO;
		}
		if (encontrada.getSituacao() == SituacaoDaFatura.PAGA) {
			return Resultado.JA_PAGA;
		}
		PagamentoConfirmado pagamento = acesso.gateway().pagamento(acesso.credencial(), idNoGateway).orElse(null);
		if (pagamento == null) {
			return Resultado.NAO_PAGA;
		}
		UUID condominio = encontrada.getTenantId();
		Resultado resultado = TenantAtual.executarComo(condominio, () -> transacao.execute(s -> pagar(encontrada.getId(), pagamento)));
		if (resultado == Resultado.PAGA) {
			reativacao.verificar(condominio);
		}
		return resultado;
	}

	private Resultado pagar(UUID faturaId, PagamentoConfirmado pagamento) {
		Fatura fatura = faturas.findById(faturaId).orElseThrow();
		if (fatura.getSituacao() == SituacaoDaFatura.PAGA) {
			return Resultado.JA_PAGA; // outro aviso chegou primeiro
		}
		if (fatura.getSituacao() == SituacaoDaFatura.CANCELADA) {
			log.warn("Fatura {} cancelada, mas paga no gateway em {}: pagamento em duplicidade", faturaId, pagamento.data());
			return Resultado.PAGA_EM_DUPLICIDADE;
		}
		fatura.paga(pagamento);
		return Resultado.PAGA;
	}

}
