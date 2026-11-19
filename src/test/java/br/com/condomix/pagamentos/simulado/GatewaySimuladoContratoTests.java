package br.com.condomix.pagamentos.simulado;

import br.com.condomix.pagamentos.AmbienteDoGateway;
import br.com.condomix.pagamentos.GatewayDePagamento;
import br.com.condomix.pagamentos.GatewayDePagamento.Credencial;
import br.com.condomix.pagamentos.GatewayDePagamento.PedidoDeBoleto;
import br.com.condomix.pagamentos.GatewayDePagamentoContrato;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** O gateway simulado cumpre o mesmo contrato do Asaas. */
class GatewaySimuladoContratoTests extends GatewayDePagamentoContrato {

	private final GatewaySimulado gateway = new GatewaySimulado(
			Clock.fixed(HOJE.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant(), ZoneId.of("America/Sao_Paulo")));

	@Override
	protected GatewayDePagamento gateway() {
		return gateway;
	}

	@Override
	protected Credencial credencialValida() {
		return new Credencial(AmbienteDoGateway.SANDBOX, "chave-simulada");
	}

	@Override
	protected Credencial credencialRecusada() {
		return new Credencial(AmbienteDoGateway.SANDBOX, "invalida");
	}

	@Override
	protected void aceitarChave(Credencial credencial) {
	}

	@Override
	protected void recusarChave(Credencial credencial) {
	}

	@Override
	protected void aceitarEmissao(PedidoDeBoleto pedido, String idEsperado) {
		// a memória aceita qualquer pedido válido
	}

	@Override
	protected void recusarPagadorSemDocumento(PedidoDeBoleto pedido) {
	}

	@Override
	protected void aindaNaoPago(String id) {
	}

	@Override
	protected void pago(String id, LocalDate data, BigDecimal valor, BigDecimal tarifa) {
		gateway.simularPagamento(id, data, valor, GatewayDePagamento.FormaDoPagamento.PIX);
	}

	@Override
	protected void inexistente(String id) {
	}

}
