package br.com.condomix.financeiro.dominio;

public enum OrigemDoLancamento {
	/** Digitado pelo síndico (despesa, receita avulsa). */
	MANUAL,
	/** Criado sozinho ao registrar o pagamento de uma cobrança. */
	COBRANCA,
	TRANSFERENCIA
}
