package br.com.condomix.financeiro.dominio;

/**
 * O que compõe uma cobrança. Os descontos têm valor negativo. Cada tipo sabe em qual categoria do
 * caixa entra quando a cobrança é paga (sem switch: SOLID, O).
 */
public enum TipoDeItem {

	TAXA_ORDINARIA(CategoriaPadrao.TAXA_CONDOMINIAL),
	FUNDO_RESERVA(CategoriaPadrao.FUNDO_RESERVA),
	TAXA_EXTRA(CategoriaPadrao.TAXA_EXTRA),
	/** Reduz a taxa condominial recebida. */
	DESCONTO_MANDATO(CategoriaPadrao.TAXA_CONDOMINIAL),
	RESERVA_DE_ESPACO(CategoriaPadrao.ALUGUEL_DE_ESPACOS),
	MULTA_DISCIPLINAR(CategoriaPadrao.MULTAS_DISCIPLINARES),
	CONSUMO(CategoriaPadrao.CONSUMO),
	OUTRO(CategoriaPadrao.OUTRAS_RECEITAS);

	private final CategoriaPadrao categoriaNoCaixa;

	TipoDeItem(CategoriaPadrao categoriaNoCaixa) {
		this.categoriaNoCaixa = categoriaNoCaixa;
	}

	public CategoriaPadrao categoriaNoCaixa() {
		return categoriaNoCaixa;
	}

}
