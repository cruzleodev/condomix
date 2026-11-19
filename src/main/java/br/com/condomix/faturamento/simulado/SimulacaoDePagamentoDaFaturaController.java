package br.com.condomix.faturamento.simulado;

import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.faturamento.SituacaoDaFatura;
import br.com.condomix.faturamento.aplicacao.ConsultaDeFaturas;
import br.com.condomix.faturamento.aplicacao.RecebimentoDeFaturas;
import br.com.condomix.pagamentos.FalhaNoGateway;
import br.com.condomix.pagamentos.GatewayDePagamento.FormaDoPagamento;
import br.com.condomix.pagamentos.SimuladorDePagamentos;
import br.com.condomix.pagamentos.TipoDeGateway;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Só em desenvolvimento e testes: faz de conta que o condomínio pagou a fatura no gateway simulado e dispara a
 * mesma baixa do aviso (webhook) de um gateway de verdade. Rota da plataforma: só o administrador.
 */
@Tag(name = "Plataforma: faturas")
@RestController
@ConditionalOnProperty(name = "condomix.gateway.simulado", havingValue = "true")
class SimulacaoDePagamentoDaFaturaController {

	private final SimuladorDePagamentos simulador;

	private final ConsultaDeFaturas faturas;

	private final RecebimentoDeFaturas recebimento;

	private final Clock relogio;

	SimulacaoDePagamentoDaFaturaController(SimuladorDePagamentos simulador, ConsultaDeFaturas faturas,
			RecebimentoDeFaturas recebimento, Clock relogio) {
		this.simulador = simulador;
		this.faturas = faturas;
		this.recebimento = recebimento;
		this.relogio = relogio;
	}

	record FaturaPagaNaSimulacao(RecebimentoDeFaturas.Resultado resultado) {
	}

	@Operation(summary = "Simular o pagamento da fatura (gateway simulado, só em desenvolvimento)")
	@PostMapping("/api/plataforma/faturas/{id}/pagamento-simulado")
	FaturaPagaNaSimulacao simular(@PathVariable UUID id) {
		var fatura = faturas.daPlataforma(id);
		if (fatura.getSituacao() != SituacaoDaFatura.EMITIDA || fatura.getGateway() != TipoDeGateway.SIMULADO) {
			throw RegraDeNegocioException.invalido("Só dá para simular o pagamento de uma fatura em aberto do gateway simulado");
		}
		try {
			simulador.simularPagamento(fatura.getIdNoGateway(), LocalDate.now(relogio), null, FormaDoPagamento.PIX);
		}
		catch (FalhaNoGateway e) {
			throw RegraDeNegocioException.invalido(e.getMessage());
		}
		return new FaturaPagaNaSimulacao(recebimento.confirmar(TipoDeGateway.SIMULADO, fatura.getIdNoGateway()));
	}

}
