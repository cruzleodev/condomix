package br.com.condomix.pagamentos;

import br.com.condomix.pagamentos.GatewayDePagamento.FormaDoPagamento;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * O gateway simulado visto por quem quer "fazer de conta que pagou" (botões de desenvolvimento).
 * Só existe com {@code condomix.gateway.simulado=true}.
 */
public interface SimuladorDePagamentos {

	/** Tarifa fixa por pagamento, parecida com a do Asaas. */
	BigDecimal TARIFA = new BigDecimal("1.99");

	/** Faz de conta que o boleto foi pago. Sem {@code valor}, paga o valor do boleto. */
	void simularPagamento(String idNoGateway, LocalDate data, BigDecimal valor, FormaDoPagamento forma);

}
