package br.com.condomix.faturamento.dominio;

import br.com.condomix.plataforma.Periodicidade;

import java.time.LocalDate;
import java.util.Optional;

/**
 * As datas da cobrança da assinatura. Cada fatura cobra um período (mês, semestre ou ano) e é paga antes de ele
 * começar: sai {@value #ANTECEDENCIA} dias antes e vence no primeiro dia do período. Depois de
 * {@value #TOLERANCIA} dias de atraso, o acesso do condomínio é suspenso.
 */
public final class CalendarioDeFaturas {

	/** Quantos dias antes do período a fatura é gerada. */
	public static final int ANTECEDENCIA = 10;

	/** Prazo mínimo para pagar uma fatura gerada em cima da hora (a primeira, ou depois de uma suspensão). */
	public static final int PRAZO_MINIMO = 7;

	/** Dias de atraso tolerados antes de suspender o acesso. */
	public static final int TOLERANCIA = 15;

	private CalendarioDeFaturas() {
	}

	/** O período de uma fatura e quando ela vence. */
	public record Periodo(LocalDate inicio, LocalDate fim, LocalDate vencimento) {
	}

	/**
	 * O próximo período a faturar, se já é hora de gerar a fatura. O período começa onde o último terminou; se o
	 * último terminou no passado (primeira fatura, ou volta de uma suspensão), começa hoje: o tempo sem acesso
	 * não é cobrado.
	 *
	 * @param fimDaUltima onde terminou o último período faturado; vazio se nunca houve fatura
	 */
	public static Optional<Periodo> proximo(LocalDate fimDaUltima, LocalDate hoje, Periodicidade periodicidade) {
		LocalDate inicio = fimDaUltima == null || fimDaUltima.isBefore(hoje) ? hoje : fimDaUltima;
		if (inicio.isAfter(hoje.plusDays(ANTECEDENCIA))) {
			return Optional.empty();
		}
		LocalDate prazoMinimo = hoje.plusDays(PRAZO_MINIMO);
		LocalDate vencimento = inicio.isBefore(prazoMinimo) ? prazoMinimo : inicio;
		return Optional.of(new Periodo(inicio, inicio.plusMonths(periodicidade.meses()), vencimento));
	}

	/** O dia em que o acesso é suspenso se a fatura não for paga. */
	public static LocalDate suspensaoEm(LocalDate vencimento) {
		return vencimento.plusDays(TOLERANCIA + 1L);
	}

	/** Faturas emitidas com vencimento antes deste dia já passaram da tolerância. */
	public static LocalDate limiteDaTolerancia(LocalDate hoje) {
		return hoje.minusDays(TOLERANCIA);
	}

}
