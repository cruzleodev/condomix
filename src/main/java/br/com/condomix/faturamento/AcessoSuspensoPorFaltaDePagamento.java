package br.com.condomix.faturamento;

import java.util.UUID;

/** O acesso do condomínio foi suspenso pela rotina: a fatura passou da tolerância. */
public record AcessoSuspensoPorFaltaDePagamento(UUID condominioId) {
}
