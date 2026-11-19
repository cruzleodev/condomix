package br.com.condomix.reservas.regras;

import br.com.condomix.compartilhado.FormatoBrasileiro;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/** Reservar com a antecedência mínima e no máximo até tantos dias à frente. */
@Component
class Antecedencia implements RegraDeReserva {

	@Override
	public Optional<String> recusa(PedidoDeReserva pedido) {
		var regras = pedido.espaco().getRegras();
		if (pedido.data().isBefore(pedido.hoje())) {
			return Optional.of("Não dá para reservar uma data que já passou.");
		}
		LocalDate primeiroDia = pedido.hoje().plusDays(regras.antecedenciaMinimaDias());
		if (pedido.data().isBefore(primeiroDia)) {
			return Optional.of("Este espaço precisa ser reservado com pelo menos " + dias(regras.antecedenciaMinimaDias())
					+ " de antecedência: a partir de " + FormatoBrasileiro.data(primeiroDia) + ".");
		}
		LocalDate ultimoDia = pedido.hoje().plusDays(regras.antecedenciaMaximaDias());
		if (pedido.data().isAfter(ultimoDia)) {
			return Optional.of("Este espaço só pode ser reservado até " + dias(regras.antecedenciaMaximaDias())
					+ " à frente: até " + FormatoBrasileiro.data(ultimoDia) + ".");
		}
		return Optional.empty();
	}

	private static String dias(int quantos) {
		return quantos == 1 ? "1 dia" : quantos + " dias";
	}

}
