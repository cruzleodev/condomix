package br.com.condomix.reservas.regras;

import br.com.condomix.reservas.dominio.Espaco;

import java.time.LocalDate;
import java.util.UUID;

/** O que as regras olham numa reserva pedida. */
public record PedidoDeReserva(Espaco espaco, UUID unidadeId, LocalDate data, LocalDate hoje) {
}
