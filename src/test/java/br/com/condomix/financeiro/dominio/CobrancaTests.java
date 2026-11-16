package br.com.condomix.financeiro.dominio;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CobrancaTests {

	private static final LocalDate VENCIMENTO = LocalDate.of(2026, 3, 10);

	private final Cobranca cobranca = new Cobranca(new Cobranca.Emissao(UUID.randomUUID(), UUID.randomUUID(), null,
			OrigemCobranca.AVULSA, null, null, "Teste", VENCIMENTO,
			List.of(new ItemDeCobranca(TipoDeItem.OUTRO, "Teste", new BigDecimal("500.00"))),
			ConfiguracaoDaCobranca.padrao(UUID.randomUUID()), null));

	@Test
	void em_dia_nao_tem_encargos() {
		var e = cobranca.encargosEm(VENCIMENTO);
		assertThat(e.diasDeAtraso()).isZero();
		assertThat(e.total()).isEqualByComparingTo("500.00");
	}

	@Test
	void atrasada_tem_multa_de_2_por_cento_e_juros_de_1_por_cento_ao_mes_pro_rata() {
		var e = cobranca.encargosEm(VENCIMENTO.plusDays(15));
		assertThat(e.diasDeAtraso()).isEqualTo(15);
		assertThat(e.multa()).isEqualByComparingTo("10.00");  // 2% de 500
		assertThat(e.juros()).isEqualByComparingTo("2.50");   // 1% ao mês × 15/30 de 500
		assertThat(e.total()).isEqualByComparingTo("512.50");
	}

	@Test
	void paga_congela_os_encargos_na_data_do_pagamento_e_pode_ser_estornada() {
		cobranca.registrarPagamento(VENCIMENTO.plusDays(30), null, FormaDePagamento.PIX, null, null);
		assertThat(cobranca.getValorPago()).isEqualByComparingTo("515.00");  // 500 + 10 + 5
		assertThat(cobranca.encargosEm(VENCIMENTO.plusDays(300)).total()).isEqualByComparingTo("515.00");

		cobranca.estornarPagamento();
		assertThat(cobranca.getStatus()).isEqualTo(StatusCobranca.ABERTA);
		assertThatThrownBy(cobranca::estornarPagamento).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void multa_acima_de_2_por_cento_e_recusada() {
		var c = ConfiguracaoDaCobranca.padrao(UUID.randomUUID());
		assertThatThrownBy(() -> c.alterar(TipoDeRateio.IGUALITARIO, 10, new BigDecimal("2.5"), BigDecimal.ONE, BigDecimal.ZERO))
				.hasMessageContaining("2%");
	}

}
