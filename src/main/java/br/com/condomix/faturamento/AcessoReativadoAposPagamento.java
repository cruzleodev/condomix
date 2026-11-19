package br.com.condomix.faturamento;

import java.util.UUID;

/** O acesso voltou: a fatura atrasada foi paga (ou perdoada). */
public record AcessoReativadoAposPagamento(UUID condominioId) {
}
