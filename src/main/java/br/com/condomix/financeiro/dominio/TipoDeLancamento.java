package br.com.condomix.financeiro.dominio;

/** Se o lançamento entra (+1) ou sai (-1) da conta. Transferências não contam como receita nem despesa. */
public enum TipoDeLancamento {

	RECEITA(1, Natureza.RECEITA),
	DESPESA(-1, Natureza.DESPESA),
	TRANSFERENCIA_ENTRADA(1, null),
	TRANSFERENCIA_SAIDA(-1, null);

	private final int sinal;

	private final Natureza natureza;

	TipoDeLancamento(int sinal, Natureza natureza) {
		this.sinal = sinal;
		this.natureza = natureza;
	}

	public int sinal() {
		return sinal;
	}

	/** Vazio nas transferências. */
	public Natureza natureza() {
		return natureza;
	}

}
