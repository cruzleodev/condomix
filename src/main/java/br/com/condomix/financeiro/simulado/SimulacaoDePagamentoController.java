package br.com.condomix.financeiro.simulado;

import br.com.condomix.acesso.Permissoes;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.pagamentos.TipoDeGateway;
import br.com.condomix.financeiro.aplicacao.ConsultaDeBoletos;
import br.com.condomix.financeiro.aplicacao.RecebimentoPeloGateway;
import br.com.condomix.pagamentos.FalhaNoGateway;
import br.com.condomix.pagamentos.SimuladorDePagamentos;
import br.com.condomix.pagamentos.GatewayDePagamento.FormaDoPagamento;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Só em desenvolvimento e testes: faz de conta que o morador pagou o boleto simulado e dispara a mesma
 * baixa automática do aviso (webhook) de um gateway de verdade.
 */
@Tag(name = "Financeiro: boleto e Pix")
@RestController
@ConditionalOnProperty(name = "condomix.gateway.simulado", havingValue = "true")
class SimulacaoDePagamentoController {

	private final SimuladorDePagamentos gateway;

	private final ConsultaDeBoletos boletos;

	private final RecebimentoPeloGateway recebimento;

	private final Clock relogio;

	SimulacaoDePagamentoController(SimuladorDePagamentos gateway, ConsultaDeBoletos boletos, RecebimentoPeloGateway recebimento,
			Clock relogio) {
		this.gateway = gateway;
		this.boletos = boletos;
		this.recebimento = recebimento;
		this.relogio = relogio;
	}

	/** {@code valor} vazio: paga o valor do boleto. */
	record PedidoPagamentoSimulado(FormaDoPagamento forma, BigDecimal valor) {
	}

	record PagamentoSimulado(RecebimentoPeloGateway.Resultado resultado) {
	}

	@Operation(summary = "Simular o pagamento do boleto (gateway simulado, só em desenvolvimento)")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping("/api/cobrancas/{id}/pagamento-simulado")
	PagamentoSimulado simular(@PathVariable UUID id, @RequestBody(required = false) PedidoPagamentoSimulado pedido) {
		var boleto = boletos.daCobranca(id);
		if (boleto.getGateway() != TipoDeGateway.SIMULADO) {
			throw RegraDeNegocioException.invalido("Este boleto é de um gateway de verdade: não dá para simular o pagamento");
		}
		try {
			gateway.simularPagamento(boleto.getIdNoGateway(), LocalDate.now(relogio), pedido == null ? null : pedido.valor(),
					pedido == null || pedido.forma() == null ? FormaDoPagamento.PIX : pedido.forma());
		}
		catch (FalhaNoGateway e) {
			throw RegraDeNegocioException.invalido(e.getMessage());
		}
		return new PagamentoSimulado(recebimento.confirmar(TenantAtual.obrigatorio(), TipoDeGateway.SIMULADO, boleto.getIdNoGateway()));
	}

}
