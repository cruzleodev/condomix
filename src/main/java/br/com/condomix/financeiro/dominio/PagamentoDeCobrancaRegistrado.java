package br.com.condomix.financeiro.dominio;

import java.util.UUID;

/** Publicado ao registrar o pagamento de uma cobrança. O caixa reage criando as receitas. */
public record PagamentoDeCobrancaRegistrado(UUID cobrancaId) {
}
