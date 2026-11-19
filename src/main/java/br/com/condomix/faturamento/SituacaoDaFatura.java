package br.com.condomix.faturamento;

/** A vida de uma fatura da assinatura. "Atrasada" não é situação: é uma emitida com o vencimento no passado. */
public enum SituacaoDaFatura {

	/** Gerada; o boleto é emitido no gateway em alguns segundos. */
	A_EMITIR,

	/** Boleto e Pix emitidos: esperando o pagamento. */
	EMITIDA,

	/** O gateway recusou (o motivo fica na fatura). O administrador corrige e manda tentar de novo. */
	FALHOU,

	PAGA,

	/** Cancelada pelo administrador; o boleto ainda vai ser cancelado no gateway. */
	A_CANCELAR,

	CANCELADA

}
