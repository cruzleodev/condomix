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

	REATIVACAO,

	/** O administrador mudou o fim do teste grátis. */
	PRAZO_DO_TESTE,

	/** O teste grátis acabou: a assinatura virou ativa sozinha e passou a ser faturada. */
	FIM_DO_TESTE,

	/** A fatura passou da tolerância sem pagamento: o acesso foi suspenso sozinho. */
	SUSPENSAO_POR_FALTA_DE_PAGAMENTO,

	/** A fatura atrasada foi paga: o acesso voltou sozinho. */
	REATIVACAO_APOS_PAGAMENTO

}
