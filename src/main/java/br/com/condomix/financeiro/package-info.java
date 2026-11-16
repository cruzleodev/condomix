/**
 * Financeiro do condomínio: o dinheiro que moradores e condôminos pagam ao condomínio.
 * (A assinatura que o condomínio paga ao Condomix fica no módulo plataforma.)
 *
 * <p>Subpacotes (internos):
 * <ul>
 *   <li>{@code dominio}: cobrança, itens, encargos (multa e juros), configuração e grupos;</li>
 *   <li>{@code rateio}: as regras de divisão da taxa (igual, fração ideal, valor por grupo);</li>
 *   <li>{@code aplicacao}: os casos de uso (geração mensal, baixa, avulsa, consulta, importação);</li>
 *   <li>{@code persistencia} e {@code web}.</li>
 * </ul>
 */
package br.com.condomix.financeiro;
