package br.com.condomix.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Lembrete do dia: a cobrança em aberto vence daqui a {@code LembretesDeCobranca.DIAS_ANTES} dias. */
public record CobrancaVenceEmBreve(UUID condominioId, UUID cobrancaId, UUID unidadeId, String descricao, BigDecimal valor,
		LocalDate vencimento) {
}
