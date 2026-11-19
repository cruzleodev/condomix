package br.com.condomix.reservas;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Uma reserva foi feita (pelo morador ou pela gestão, para a unidade). */
public record ReservaFeita(UUID condominioId, UUID reservaId, UUID unidadeId, String espaco, LocalDate data, String horario,
		LocalTime inicio, LocalTime fim, BigDecimal valor, boolean pelaGestao) {
}
