package br.com.condomix.plataforma;

import java.math.BigDecimal;
import java.util.function.Function;

/** De quanto em quanto tempo a assinatura é faturada, e o preço do plano em cada caso. */
public enum Periodicidade {

	MENSAL(1, Plano::getPrecoMensal),
	SEMESTRAL(6, Plano::getPrecoSemestral),
	ANUAL(12, Plano::getPrecoAnual);

	private final int meses;

	private final Function<Plano, BigDecimal> preco;

	Periodicidade(int meses, Function<Plano, BigDecimal> preco) {
		this.meses = meses;
		this.preco = preco;
	}

	/** Quantos meses cada fatura cobre. */
	public int meses() {
		return meses;
	}

	/** O preço do plano nesta periodicidade; vazio se o administrador ainda não definiu. */
	public BigDecimal precoDe(Plano plano) {
		return preco.apply(plano);
	}

}
