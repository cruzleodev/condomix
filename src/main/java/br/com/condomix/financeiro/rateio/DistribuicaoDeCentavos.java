package br.com.condomix.financeiro.rateio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Divide um total em reais conforme os pesos, garantindo que a soma das partes dê exatamente o total.
 * Método do maior resto: cada parte recebe o valor truncado nos centavos, e os centavos que sobram vão,
 * um a um, para as partes com a maior fração descartada.
 */
public final class DistribuicaoDeCentavos {

	private DistribuicaoDeCentavos() {
	}

	public static Map<UUID, BigDecimal> distribuir(BigDecimal total, Map<UUID, BigDecimal> pesos) {
		BigDecimal somaDosPesos = pesos.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		if (somaDosPesos.signum() <= 0) {
			throw new IllegalArgumentException("Não há como dividir: a soma dos pesos é zero");
		}
		long totalEmCentavos = total.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();

		record Parte(UUID id, long centavos, BigDecimal resto) {
		}
		List<Parte> partes = pesos.entrySet().stream().map(e -> {
			BigDecimal exato = BigDecimal.valueOf(totalEmCentavos).multiply(e.getValue())
					.divide(somaDosPesos, 12, RoundingMode.HALF_UP);
			BigDecimal inteiro = exato.setScale(0, RoundingMode.DOWN);
			return new Parte(e.getKey(), inteiro.longValueExact(), exato.subtract(inteiro));
		}).toList();

		long sobra = totalEmCentavos - partes.stream().mapToLong(Parte::centavos).sum();
		List<UUID> recebemCentavo = partes.stream()
				.sorted(Comparator.comparing(Parte::resto).reversed().thenComparing(Parte::id))
				.limit(sobra)
				.map(Parte::id)
				.toList();

		Map<UUID, BigDecimal> resultado = new LinkedHashMap<>();
		for (Parte p : partes) {
			long centavos = p.centavos() + (recebemCentavo.contains(p.id()) ? 1 : 0);
			resultado.put(p.id(), BigDecimal.valueOf(centavos).movePointLeft(2));
		}
		return resultado;
	}

}
