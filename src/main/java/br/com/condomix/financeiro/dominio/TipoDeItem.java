package br.com.condomix.financeiro.dominio;

/** O que compõe uma cobrança. Os descontos têm valor negativo. */
public enum TipoDeItem {
	TAXA_ORDINARIA, FUNDO_RESERVA, TAXA_EXTRA, DESCONTO_MANDATO, RESERVA_DE_ESPACO, MULTA_DISCIPLINAR, CONSUMO, OUTRO
}
