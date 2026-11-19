package br.com.condomix.faturamento.aplicacao;

import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.faturamento.FaturaAtrasada;
import br.com.condomix.faturamento.SituacaoDaFatura;
import br.com.condomix.faturamento.TesteGratisTerminando;
import br.com.condomix.faturamento.dominio.CalendarioDeFaturas;
import br.com.condomix.faturamento.persistencia.FaturaRepository;
import br.com.condomix.plataforma.AssinaturasParaFaturamento;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Todo dia de manhã, para a gestão: a fatura que venceu ontem sem pagamento e o teste grátis que acaba em
 * {@value #DIAS_ANTES_DO_FIM_DO_TESTE} dias. Rodar de novo no mesmo dia não repete o aviso.
 */
@Component
public class LembretesDaAssinatura {

	public static final int DIAS_ANTES_DO_FIM_DO_TESTE = 7;

	private final FaturaRepository faturas;

	private final AssinaturasParaFaturamento assinaturas;

	private final ApplicationEventPublisher eventos;

	private final TransactionTemplate transacao;

	private final Clock relogio;

	public LembretesDaAssinatura(FaturaRepository faturas, AssinaturasParaFaturamento assinaturas,
			ApplicationEventPublisher eventos, TransactionTemplate transacao, Clock relogio) {
		this.faturas = faturas;
		this.assinaturas = assinaturas;
		this.eventos = eventos;
		this.transacao = transacao;
		this.relogio = relogio;
	}

	@Scheduled(cron = "${condomix.lembretes.horario:0 0 8 * * *}", zone = "${condomix.fuso-horario:America/Sao_Paulo}")
	public void lembrar() {
		LocalDate hoje = LocalDate.now(relogio);
		// Faturas são dados da plataforma: a rotina lê as de todos os condomínios.
		TenantAtual.comoSistema(() -> {
			transacao.executeWithoutResult(s -> faturas.findBySituacaoAndVencimento(SituacaoDaFatura.EMITIDA, hoje.minusDays(1))
					.forEach(f -> eventos.publishEvent(new FaturaAtrasada(f.getTenantId(), f.getId(), f.getValor(), f.getVencimento(),
							CalendarioDeFaturas.suspensaoEm(f.getVencimento())))));
			return null;
		});
		var terminando = assinaturas.testesTerminandoEm(hoje.plusDays(DIAS_ANTES_DO_FIM_DO_TESTE));
		transacao.executeWithoutResult(s -> terminando.forEach(t -> eventos.publishEvent(new TesteGratisTerminando(
				t.condominioId(), t.fimDoTeste(), t.nomeDoPlano(), t.periodicidade(), t.preco()))));
	}

}
