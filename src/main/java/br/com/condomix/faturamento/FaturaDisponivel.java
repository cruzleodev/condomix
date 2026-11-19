package br.com.condomix.faturamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** A fatura da assinatura ganhou boleto e Pix: o síndico já pode pagar. */
public record FaturaDisponivel(UUID condominioId, UUID faturaId, BigDecimal valor, LocalDate vencimento,
		LocalDate periodoInicio, LocalDate periodoFim) {
}
