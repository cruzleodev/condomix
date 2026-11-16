package br.com.condomix.financeiro.dominio;

public enum OrigemCobranca {
	/** Taxa do mês, gerada para todas as unidades. */
	GERACAO_MENSAL,
	/** Lançada à parte: multa disciplinar, aluguel de espaço, consumo... */
	AVULSA,
	/** Trazida do sistema da administradora. */
	IMPORTACAO
}
