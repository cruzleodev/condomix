package br.com.condomix.plataforma;

/** O que mudou na assinatura de um cliente (ver {@link PainelDeCondominios#historico}). */
public enum TipoDeEventoDaAssinatura {

	CLIENTE_NOVO,

	/** Trocou o plano ou a periodicidade: a assinatura anterior é encerrada e uma nova começa. */
	TROCA_DE_PLANO,

	/** Mudou só a situação (em teste, ativa, suspensa). */
	MUDANCA_DE_SITUACAO,

	/** O acesso do condomínio ao app foi suspenso. */
	SUSPENSAO,

	REATIVACAO

}
