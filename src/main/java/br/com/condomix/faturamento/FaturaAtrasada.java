package br.com.condomix.faturamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Lembrete do dia: a fatura venceu ontem sem pagamento. {@code suspensaoEm}: quando o acesso será suspenso. */
public record FaturaAtrasada(UUID condominioId, UUID faturaId, BigDecimal valor, LocalDate vencimento, LocalDate suspensaoEm) {
}
