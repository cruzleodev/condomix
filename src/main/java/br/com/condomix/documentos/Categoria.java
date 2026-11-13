package br.com.condomix.documentos;

/** Categorias de documento, com o nome para exibir na tela. */
public enum Categoria {

	CONVENCAO("Convenção"),
	REGIMENTO_INTERNO("Regimento interno"),
	ATA_ASSEMBLEIA("Ata de assembleia"),
	LEI_NORMA("Leis e normas"),
	COMUNICADO("Comunicado"),
	OUTRO("Outros");

	private final String rotulo;

	Categoria(String rotulo) {
		this.rotulo = rotulo;
	}

	public String rotulo() {
		return rotulo;
	}

}
