package br.com.condomix.reservas.regras;

import br.com.condomix.reservas.dominio.Espaco;
import br.com.condomix.reservas.dominio.PeriodoDoEspaco;
import br.com.condomix.reservas.dominio.RegrasDoEspaco;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O contrato de toda {@link RegraDeReserva} (SOLID: L): deixa passar um pedido comum e recusa o caso dela com uma
 * mensagem em português, sem lançar exceção. Cada implementação tem uma subclasse.
 */
abstract class RegraDeReservaContrato {

	protected static final LocalDate HOJE = LocalDate.of(2026, 11, 19);

	protected static final UUID UNIDADE = UUID.randomUUID();

	protected abstract RegraDeReserva regra();

	/** Um pedido que a regra deixa passar. */
	protected abstract PedidoDeReserva pedidoQuePassa();

	/** Um pedido que a regra recusa. */
	protected abstract PedidoDeReserva pedidoRecusado();

	@Test
	void deixaPassarUmPedidoComum() {
		assertThat(regra().recusa(pedidoQuePassa())).isEmpty();
	}

	@Test
	void recusaComUmaMensagemParaOMorador() {
		assertThat(regra().recusa(pedidoRecusado())).hasValueSatisfying(motivo -> {
			assertThat(motivo).isNotBlank();
			assertThat(motivo).endsWith(".");
		});
	}

	protected static Espaco espaco(RegrasDoEspaco regras) {
		return new Espaco(UUID.randomUUID(), new Espaco.DadosDoEspaco("Salão", null, null, BigDecimal.ZERO, regras,
				List.of(new PeriodoDoEspaco("Noite", LocalTime.of(18, 0), LocalTime.of(23, 0)))));
	}

}
