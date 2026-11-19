/**
 * Financeiro do condomínio: o dinheiro que moradores e condôminos pagam ao condomínio.
 * (A assinatura que o condomínio paga ao Condomix fica no módulo plataforma.)
 *
 * <p>API pública para outros módulos: {@code GatewayDoCondominio} (o painel da plataforma liga o boleto e o Pix),
 * {@code TipoDeGateway} e {@code AmbienteDoGateway}.
 *
 * <p>Subpacotes (internos):
 * <ul>
 *   <li>{@code dominio}: cobrança, itens, encargos (multa e juros), configuração e grupos;</li>
 *   <li>{@code rateio}: as regras de divisão da taxa (igual, fração ideal, valor por grupo);</li>
 *   <li>{@code aplicacao}: os casos de uso (geração mensal, baixa, avulsa, consulta, importação, boletos);</li>
 *   <li>{@code gateway}: a interface {@code GatewayDePagamento} (boleto e Pix), com as implementações em
 *       {@code asaas} (de verdade) e {@code simulado} (desenvolvimento e testes);</li>
 *   <li>{@code persistencia} e {@code web}.</li>
 * </ul>
 */
package br.com.condomix.financeiro;
