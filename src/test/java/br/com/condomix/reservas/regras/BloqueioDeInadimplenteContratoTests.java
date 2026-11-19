package br.com.condomix.reservas.regras;

import br.com.condomix.financeiro.CobrancasParaReservas;
import br.com.condomix.reservas.dominio.RegrasDoEspaco;
import org.junit.jupiter.api.BeforeEach;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BloqueioDeInadimplenteContratoTests extends RegraDeReservaContrato {

	private static final UUID EM_DIA = UUID.randomUUID();

	private final CobrancasParaReservas cobrancas = mock(CobrancasParaReservas.class);

	private final RegrasDoEspaco bloqueia = new RegrasDoEspaco(0, 90, null, true, 0);

	@BeforeEach
	void unidadeComCobrancaVencida() {
		when(cobrancas.unidadeInadimplente(eq(UNIDADE), any())).thenReturn(true);
		when(cobrancas.unidadeInadimplente(eq(EM_DIA), any())).thenReturn(false);
	}

	@Override
	protected RegraDeReserva regra() {
		return new BloqueioDeInadimplente(cobrancas);
	}

	@Override
	protected PedidoDeReserva pedidoQuePassa() {
		return new PedidoDeReserva(espaco(bloqueia), EM_DIA, HOJE.plusDays(3), HOJE);
	}

	@Override
	protected PedidoDeReserva pedidoRecusado() {
		return new PedidoDeReserva(espaco(bloqueia), UNIDADE, HOJE.plusDays(3), HOJE);
	}

}
