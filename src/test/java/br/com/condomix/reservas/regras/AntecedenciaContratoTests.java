package br.com.condomix.reservas.regras;

import br.com.condomix.reservas.dominio.RegrasDoEspaco;

class AntecedenciaContratoTests extends RegraDeReservaContrato {

	private final RegrasDoEspaco regras = new RegrasDoEspaco(2, 30, null, true, 0);

	@Override
	protected RegraDeReserva regra() {
		return new Antecedencia();
	}

	@Override
	protected PedidoDeReserva pedidoQuePassa() {
		return new PedidoDeReserva(espaco(regras), UNIDADE, HOJE.plusDays(2), HOJE);
	}

	@Override
	protected PedidoDeReserva pedidoRecusado() {
		return new PedidoDeReserva(espaco(regras), UNIDADE, HOJE.plusDays(31), HOJE);
	}

}
