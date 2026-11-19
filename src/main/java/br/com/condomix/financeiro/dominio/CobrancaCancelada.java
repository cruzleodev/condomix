package br.com.condomix.financeiro.dominio;

import java.util.UUID;

/** Publicado ao cancelar uma cobrança. O boleto dela, se houver, é cancelado no gateway. */
public record CobrancaCancelada(UUID cobrancaId) {
}
