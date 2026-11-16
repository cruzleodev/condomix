package br.com.condomix.financeiro.dominio;

import java.math.BigDecimal;

/**
 * O que uma cobrança aberta vale numa data: o valor original mais multa e juros, se estiver atrasada.
 *
 * @param diasDeAtraso 0 quando não venceu
 */
public record Encargos(long diasDeAtraso, BigDecimal principal, BigDecimal multa, BigDecimal juros, BigDecimal total) {
}
