package br.com.condomix.financeiro.rateio;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DistribuicaoDeCentavosTests {

	@Test
	void mil_reais_para_tres_unidades_iguais_fecham_exatamente() {
		Map<UUID, BigDecimal> pesos = new LinkedHashMap<>();
		for (int i = 0; i < 3; i++) {
			pesos.put(UUID.randomUUID(), BigDecimal.ONE);
		}
		var partes = DistribuicaoDeCentavos.distribuir(new BigDecimal("1000.00"), pesos);

		assertThat(partes.values()).containsExactlyInAnyOrder(new BigDecimal("333.34"), new BigDecimal("333.33"),
				new BigDecimal("333.33"));
		assertThat(partes.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("1000.00");
	}

	@Test
	void fracoes_que_nao_somam_um_sao_proporcionais_e_o_total_se_mantem() {
		Map<UUID, BigDecimal> pesos = new LinkedHashMap<>();
		pesos.put(UUID.randomUUID(), new BigDecimal("0.015"));
		pesos.put(UUID.randomUUID(), new BigDecimal("0.030"));
		pesos.put(UUID.randomUUID(), new BigDecimal("0.0175"));

		var partes = DistribuicaoDeCentavos.distribuir(new BigDecimal("777.77"), pesos);

		assertThat(partes.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("777.77");
	}

}
