package br.com.condomix.financeiro.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Arredondamento de valores em reais: 2 casas, metade para cima (como os bancos). */
public final class Dinheiro {

	public static final BigDecimal CEM = BigDecimal.valueOf(100);

	private Dinheiro() {
	}

	public static BigDecimal reais(BigDecimal valor) {
		return valor.setScale(2, RoundingMode.HALF_UP);
	}

	/** {@code valor × percentual / 100}, em reais. */
	public static BigDecimal percentual(BigDecimal valor, BigDecimal percentual) {
		return reais(valor.multiply(percentual).divide(CEM, 10, RoundingMode.HALF_UP));
	}

}
