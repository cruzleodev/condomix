package br.com.condomix.comunicacao.dominio;

public enum SituacaoDaEntrega {
	PENDENTE,
	ENVIADA,
	/** Desistiu depois de várias tentativas. */
	FALHOU,
	/** Não havia como entregar (ex.: push sem nenhum aparelho inscrito). */
	DESCARTADA
}
