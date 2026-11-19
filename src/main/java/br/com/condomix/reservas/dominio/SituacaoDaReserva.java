package br.com.condomix.reservas.dominio;

/** A reserva vale assim que é feita (passou nas regras). "Realizada" não é situação: é uma confirmada no passado. */
public enum SituacaoDaReserva {
	CONFIRMADA, CANCELADA
}
