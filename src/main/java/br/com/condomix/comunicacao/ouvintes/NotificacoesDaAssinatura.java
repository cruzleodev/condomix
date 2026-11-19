package br.com.condomix.comunicacao.ouvintes;

import br.com.condomix.comunicacao.aplicacao.CentralDeNotificacoes;
import br.com.condomix.comunicacao.aplicacao.CentralDeNotificacoes.NovaNotificacao;
import br.com.condomix.comunicacao.dominio.GrupoDeNotificacao;
import br.com.condomix.faturamento.AcessoReativadoAposPagamento;
import br.com.condomix.faturamento.AcessoSuspensoPorFaltaDePagamento;
import br.com.condomix.faturamento.FaturaAtrasada;
import br.com.condomix.faturamento.FaturaDisponivel;
import br.com.condomix.faturamento.TesteGratisTerminando;
import br.com.condomix.plataforma.Periodicidade;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

import static br.com.condomix.compartilhado.FormatoBrasileiro.data;
import static br.com.condomix.compartilhado.FormatoBrasileiro.moeda;

/** A assinatura do Condomix: avisa a gestão (síndico e administradora). */
@Component
class NotificacoesDaAssinatura {

	private static final String TELA = "/gestao/assinatura";

	private static final Map<Periodicidade, String> POR = Map.of(Periodicidade.MENSAL, "por mês", Periodicidade.SEMESTRAL,
			"por semestre", Periodicidade.ANUAL, "por ano");

	private final CentralDeNotificacoes central;

	private final Clock relogio;

	NotificacoesDaAssinatura(CentralDeNotificacoes central, Clock relogio) {
		this.central = central;
		this.relogio = relogio;
	}

	@Async
	@TransactionalEventListener
	void faturaDisponivel(FaturaDisponivel e) {
		central.notificar(e.condominioId(), d -> d.daGestao(), new NovaNotificacao(GrupoDeNotificacao.ASSINATURA,
				"fatura-disponivel:" + e.faturaId(), "Fatura do Condomix disponível",
				"Assinatura de " + data(e.periodoInicio()) + " a " + data(e.periodoFim().minusDays(1)) + ": " + moeda(e.valor())
						+ ", vence em " + data(e.vencimento()) + ". Pague com Pix ou boleto em Gestão → Assinatura.",
				TELA));
	}

	@Async
	@TransactionalEventListener
	void faturaAtrasada(FaturaAtrasada e) {
		central.notificar(e.condominioId(), d -> d.daGestao(), new NovaNotificacao(GrupoDeNotificacao.ASSINATURA,
				"fatura-atrasada:" + e.faturaId(), "Fatura do Condomix vencida",
				"A fatura de " + moeda(e.valor()) + " venceu em " + data(e.vencimento()) + ". Se não for paga, o acesso do condomínio "
						+ "será suspenso em " + data(e.suspensaoEm()) + ".",
				TELA));
	}

	@Async
	@TransactionalEventListener
	void acessoSuspenso(AcessoSuspensoPorFaltaDePagamento e) {
		central.notificar(e.condominioId(), d -> d.daGestao(), new NovaNotificacao(GrupoDeNotificacao.ASSINATURA,
				"acesso-suspenso:" + e.condominioId() + ":" + LocalDate.now(relogio), "Acesso ao Condomix suspenso",
				"A fatura do Condomix passou de 15 dias de atraso. Moradores e gestão não conseguem entrar até ela ser paga; "
						+ "você ainda entra no app, só para pagar.",
				TELA));
	}

	@Async
	@TransactionalEventListener
	void acessoReativado(AcessoReativadoAposPagamento e) {
		central.notificar(e.condominioId(), d -> d.daGestao(), new NovaNotificacao(GrupoDeNotificacao.ASSINATURA,
				"acesso-reativado:" + e.condominioId() + ":" + LocalDate.now(relogio), "Acesso ao Condomix liberado",
				"Recebemos o pagamento da fatura. O acesso de todos voltou ao normal.", TELA));
	}

	@Async
	@TransactionalEventListener
	void testeTerminando(TesteGratisTerminando e) {
		String valor = e.preco() == null ? "" : ": " + moeda(e.preco()) + " " + POR.get(e.periodicidade());
		central.notificar(e.condominioId(), d -> d.daGestao(), new NovaNotificacao(GrupoDeNotificacao.ASSINATURA,
				"teste-terminando:" + e.condominioId() + ":" + e.fimDoTeste(), "Seu teste grátis termina em breve",
				"O teste do plano " + e.plano() + " termina em " + data(e.fimDoTeste()) + ". Depois, a assinatura passa a ser cobrada"
						+ valor + ", com a fatura em Gestão → Assinatura.",
				TELA));
	}

}
