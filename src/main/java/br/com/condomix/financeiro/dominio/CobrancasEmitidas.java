package br.com.condomix.financeiro.dominio;

import java.util.List;
import java.util.UUID;

/** Publicado ao criar cobranças (taxa do mês ou avulsa). Com o gateway ligado, cada uma ganha o seu boleto. */
public record CobrancasEmitidas(List<UUID> cobrancaIds) {
}
