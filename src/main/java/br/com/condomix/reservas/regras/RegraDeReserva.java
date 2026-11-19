package br.com.condomix.reservas.regras;

import java.util.Optional;

/**
 * Uma regra de reserva (SOLID: O). O serviço recebe todas ({@code List<RegraDeReserva>}) e aplica cada uma: para
 * uma regra nova, basta uma implementação nova. Cada espaço configura os números em {@code RegrasDoEspaco}.
 * Toda implementação passa no {@code RegraDeReservaContrato}.
 */
public interface RegraDeReserva {

	/** O motivo da recusa, em português, para o morador; vazio se a regra deixa reservar. Nunca lança. */
	Optional<String> recusa(PedidoDeReserva pedido);

}
