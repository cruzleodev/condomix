package br.com.condomix.financeiro.dominio;

/** "Vencida" não é um status: é uma cobrança ABERTA com vencimento no passado (ver {@link Cobranca#vencidaEm}). */
public enum StatusCobranca {
	ABERTA, PAGA, CANCELADA
}
