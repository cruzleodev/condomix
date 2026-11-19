package br.com.condomix.financeiro.dominio;

/** Ciclo do boleto: PENDENTE → EMITIDO → PAGO; ou FALHOU (tenta de novo), A_CANCELAR → CANCELADO. */
public enum SituacaoDoBoleto {

	/** Esperando o processador emitir no gateway. */
	PENDENTE,
	EMITIDO,
	/** O gateway recusou (ex.: pagador sem CPF). O síndico corrige e pede para tentar de novo. */
	FALHOU,
	PAGO,
	/** A cobrança foi cancelada ou paga por fora: o processador cancela o boleto no gateway. */
	A_CANCELAR,
	CANCELADO;

	/** Ainda pode ser pago pelo morador. */
	public boolean pagavel() {
		return this == EMITIDO;
	}

}
