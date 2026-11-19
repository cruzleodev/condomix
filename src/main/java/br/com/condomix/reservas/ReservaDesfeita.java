package br.com.condomix.reservas;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Uma reserva foi cancelada.
 *
 * @param aluguelMantido cancelou fora do prazo: o aluguel continua devido
 */
public record ReservaDesfeita(UUID condominioId, UUID reservaId, UUID unidadeId, String espaco, LocalDate data, String horario,
		String motivo, boolean pelaGestao, boolean aluguelMantido) {
}
