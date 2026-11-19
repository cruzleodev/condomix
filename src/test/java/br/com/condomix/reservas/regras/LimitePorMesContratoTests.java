package br.com.condomix.reservas.regras;

import br.com.condomix.reservas.dominio.RegrasDoEspaco;
import br.com.condomix.reservas.persistencia.ReservaRepository;
import org.junit.jupiter.api.BeforeEach;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LimitePorMesContratoTests extends RegraDeReservaContrato {

	private final ReservaRepository reservas = mock(ReservaRepository.class);

	private final RegrasDoEspaco umaPorMes = new RegrasDoEspaco(0, 90, 1, true, 0);

	@BeforeEach
	void unidadeJaTemUmaReservaEmDezembro() {
		when(reservas.countByEspacoIdAndUnidadeIdAndDataBetweenAndSituacao(any(), eq(UNIDADE), any(), any(), any()))
				.thenAnswer(chamada -> chamada.getArgument(2, java.time.LocalDate.class).getMonthValue() == 12 ? 1L : 0L);
	}

	@Override
	protected RegraDeReserva regra() {
		return new LimitePorMes(reservas);
	}

	@Override
	protected PedidoDeReserva pedidoQuePassa() {
		return new PedidoDeReserva(espaco(umaPorMes), UNIDADE, HOJE.plusDays(3), HOJE); // novembro
	}

	@Override
	protected PedidoDeReserva pedidoRecusado() {
		return new PedidoDeReserva(espaco(umaPorMes), UNIDADE, HOJE.plusDays(20), HOJE); // dezembro
	}

}
