package br.com.condomix.financeiro.rateio;

import java.math.BigDecimal;

final class ValorTotal {

	private ValorTotal() {
	}

	static void exigir(BigDecimal valorTotal) {
		if (valorTotal == null || valorTotal.signum() <= 0) {
			throw new IllegalArgumentException("Informe o valor total a ratear (maior que zero)");
		}
	}

}
