package br.com.condomix.reservas;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Lembrete do dia: a reserva é amanhã. */
public record ReservaAmanha(UUID condominioId, UUID reservaId, UUID unidadeId, String espaco, LocalDate data, String horario,
		LocalTime inicio, LocalTime fim) {
}
