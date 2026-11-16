package br.com.condomix.financeiro.dominio;

public enum TipoDeConta {
	CONTA_CORRENTE, POUPANCA, APLICACAO, CAIXA_FISICO,
	/** Conta no gateway de pagamentos (etapa dos boletos). */
	GATEWAY
}
