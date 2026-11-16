package br.com.condomix.financeiro.dominio;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;

@Embeddable
public record ItemDeCobranca(@Enumerated(EnumType.STRING) TipoDeItem tipo, String descricao, BigDecimal valor) {
}
