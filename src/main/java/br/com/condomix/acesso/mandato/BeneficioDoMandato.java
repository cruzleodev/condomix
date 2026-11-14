package br.com.condomix.acesso.mandato;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Desconto (ou isenção, com 100%) na taxa condominial de uma unidade enquanto o mandato vigorar.
 * Será aplicado pelo módulo financeiro.
 */
@Embeddable
public record BeneficioDoMandato(
		@Column(name = "desconto_percentual") BigDecimal percentual,
		@Column(name = "unidade_beneficiada_id") UUID unidadeId) {

	public BeneficioDoMandato {
		Objects.requireNonNull(percentual, "percentual");
		Objects.requireNonNull(unidadeId, "unidadeId");
		if (percentual.signum() < 0 || percentual.compareTo(BigDecimal.valueOf(100)) > 0) {
			throw new IllegalArgumentException("O desconto deve ficar entre 0% e 100%");
		}
	}

}
