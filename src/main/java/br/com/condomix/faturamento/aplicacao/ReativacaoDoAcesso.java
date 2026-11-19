package br.com.condomix.faturamento.aplicacao;

import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.faturamento.SituacaoDaFatura;
import br.com.condomix.faturamento.dominio.CalendarioDeFaturas;
import br.com.condomix.faturamento.persistencia.FaturaRepository;
import br.com.condomix.plataforma.AssinaturasParaFaturamento;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/** Depois de uma fatura paga ou perdoada: sem mais nenhuma além da tolerância, o acesso suspenso por atraso volta. */
@Component
class ReativacaoDoAcesso {

	private final FaturaRepository faturas;

	private final AssinaturasParaFaturamento assinaturas;

	private final TransactionTemplate transacao;

	private final Clock relogio;

	ReativacaoDoAcesso(FaturaRepository faturas, AssinaturasParaFaturamento assinaturas, TransactionTemplate transacao,
			Clock relogio) {
		this.faturas = faturas;
		this.assinaturas = assinaturas;
		this.transacao = transacao;
		this.relogio = relogio;
	}

	void verificar(UUID condominioId) {
		LocalDate limite = CalendarioDeFaturas.limiteDaTolerancia(LocalDate.now(relogio));
		Boolean aindaDeve = TenantAtual.executarComo(condominioId, () -> transacao.execute(
				s -> faturas.existsBySituacaoAndVencimentoBefore(SituacaoDaFatura.EMITIDA, limite)));
		if (!Boolean.TRUE.equals(aindaDeve)) {
			assinaturas.reativarAposPagamento(condominioId);
		}
	}

}
