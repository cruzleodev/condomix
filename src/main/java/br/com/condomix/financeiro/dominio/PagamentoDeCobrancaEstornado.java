package br.com.condomix.financeiro.dominio;

import java.util.UUID;

/** Publicado ao estornar o pagamento de uma cobrança. O caixa reage cancelando as receitas. */
public record PagamentoDeCobrancaEstornado(UUID cobrancaId) {
}
