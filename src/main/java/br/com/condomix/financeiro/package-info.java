/**
 * Financeiro do condomínio: o dinheiro que moradores e condôminos pagam ao condomínio.
 * (A assinatura que o condomínio paga ao Condomix fica no módulo faturamento.)
 *
 * <p>API pública para outros módulos: {@code GatewayDoCondominio} (o painel da plataforma liga o boleto e o Pix).
 * A conversa com o gateway fica no módulo {@code pagamentos}.
 *
 * <p>Subpacotes (internos):
 * <ul>
 *   <li>{@code dominio}: cobrança, itens, encargos (multa e juros), configuração e grupos;</li>
 *   <li>{@code rateio}: as regras de divisão da taxa (igual, fração ideal, valor por grupo);</li>
 *   <li>{@code aplicacao}: os casos de uso (geração mensal, baixa, avulsa, consulta, importação, boletos);</li>
 *   <li>{@code asaas} e {@code simulado}: o aviso (webhook) do Asaas e o botão "simular pagamento";</li>
 *   <li>{@code persistencia} e {@code web}.</li>
 * </ul>
 */
package br.com.condomix.financeiro;
