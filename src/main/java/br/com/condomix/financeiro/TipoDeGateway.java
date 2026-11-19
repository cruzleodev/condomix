package br.com.condomix.financeiro;

/** Os gateways de pagamento disponíveis. Cada um tem a sua implementação de {@link GatewayDePagamento}. */
public enum TipoDeGateway {

	ASAAS,

	/** Boleto e Pix de mentira, para desenvolvimento e testes (só com {@code condomix.gateway.simulado=true}). */
	SIMULADO

}
