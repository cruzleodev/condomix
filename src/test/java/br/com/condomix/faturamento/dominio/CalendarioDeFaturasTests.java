package br.com.condomix.faturamento.dominio;

import br.com.condomix.faturamento.dominio.CalendarioDeFaturas.Periodo;
import br.com.condomix.plataforma.Periodicidade;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarioDeFaturasTests {

	private static final LocalDate HOJE = LocalDate.of(2026, 11, 19);

	@Test
	void primeiraFaturaComecaHojeEDaUmPrazoMinimoParaPagar() {
		assertThat(CalendarioDeFaturas.proximo(null, HOJE, Periodicidade.MENSAL))
				.contains(new Periodo(HOJE, LocalDate.of(2026, 12, 19), HOJE.plusDays(7)));
	}

	@Test
	void aSeguinteSaiDezDiasAntesEVenceNoInicioDoPeriodo() {
		LocalDate fimDaUltima = HOJE.plusDays(10);
		assertThat(CalendarioDeFaturas.proximo(fimDaUltima, HOJE, Periodicidade.MENSAL))
				.contains(new Periodo(fimDaUltima, fimDaUltima.plusMonths(1), fimDaUltima));
		assertThat(CalendarioDeFaturas.proximo(HOJE.plusDays(11), HOJE, Periodicidade.MENSAL)).isEmpty();
	}

	@Test
	void periodoSemestralEAnual() {
		assertThat(CalendarioDeFaturas.proximo(null, HOJE, Periodicidade.SEMESTRAL)).get()
				.extracting(Periodo::fim).isEqualTo(LocalDate.of(2027, 5, 19));
		assertThat(CalendarioDeFaturas.proximo(null, HOJE, Periodicidade.ANUAL)).get()
				.extracting(Periodo::fim).isEqualTo(LocalDate.of(2027, 11, 19));
	}

	@Test
	void depoisDeUmaSuspensaoOTempoSemAcessoNaoECobrado() {
		LocalDate fimDaUltima = HOJE.minusDays(40);
		assertThat(CalendarioDeFaturas.proximo(fimDaUltima, HOJE, Periodicidade.MENSAL))
				.contains(new Periodo(HOJE, HOJE.plusMonths(1), HOJE.plusDays(7)));
	}

	@Test
	void suspendeDepoisDeQuinzeDiasDeAtraso() {
		LocalDate vencimento = LocalDate.of(2026, 11, 1);
		assertThat(CalendarioDeFaturas.suspensaoEm(vencimento)).isEqualTo(LocalDate.of(2026, 11, 17));
		// Venceu antes do limite = passou da tolerância.
		assertThat(vencimento.isBefore(CalendarioDeFaturas.limiteDaTolerancia(LocalDate.of(2026, 11, 16)))).isFalse();
		assertThat(vencimento.isBefore(CalendarioDeFaturas.limiteDaTolerancia(LocalDate.of(2026, 11, 17)))).isTrue();
	}

}
