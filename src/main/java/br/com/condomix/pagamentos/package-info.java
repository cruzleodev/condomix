/**
 * Pagamentos: a ligação com os gateways (boleto e Pix). Não sabe quem cobra nem o que é cobrado: o financeiro
 * (cobranças dos moradores) e o faturamento (assinatura do Condomix) usam a mesma interface, cada um com a
 * credencial da própria conta.
 *
 * <p>API pública: {@code GatewayDePagamento}, {@code Gateways}, {@code FalhaNoGateway}, {@code TipoDeGateway},
 * {@code AmbienteDoGateway} e {@code SimuladorDePagamentos}.
 *
 * <p>Subpacotes (internos): {@code asaas} (de verdade) e {@code simulado} (desenvolvimento e testes).
 */
package br.com.condomix.pagamentos;
