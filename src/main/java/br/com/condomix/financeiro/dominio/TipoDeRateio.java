package br.com.condomix.financeiro.dominio;

/** Como a taxa ordinária é dividida entre as unidades. */
public enum TipoDeRateio {

	/** Mesmo valor para todas as unidades (unidades do mesmo tamanho). */
	IGUALITARIO("Igual para todas as unidades"),
	/** Proporcional à fração ideal (regra padrão do Código Civil, art. 1.336, I). */
	FRACAO_IDEAL("Proporcional à fração ideal"),
	/** Valor fixo definido para cada grupo de unidades (ex.: tipo, cobertura). */
	VALOR_POR_GRUPO("Valor fixo por grupo de unidades");

	private final String rotulo;

	TipoDeRateio(String rotulo) {
		this.rotulo = rotulo;
	}

	public String rotulo() {
		return rotulo;
	}

}
