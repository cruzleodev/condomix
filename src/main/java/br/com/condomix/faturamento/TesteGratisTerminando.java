package br.com.condomix.faturamento;

import br.com.condomix.plataforma.Periodicidade;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Lembrete do dia: o teste grátis acaba em uma semana. {@code preco} vazio se ainda não definido. */
public record TesteGratisTerminando(UUID condominioId, LocalDate fimDoTeste, String plano, Periodicidade periodicidade,
		BigDecimal preco) {
}
