package br.com.condomix.comunicacao.canais;

import br.com.condomix.comunicacao.dominio.GrupoDeNotificacao;

import java.util.UUID;

/** O que os canais entregam: o mesmo título, texto e link do sino do app. */
public record ConteudoDaNotificacao(UUID notificacaoId, GrupoDeNotificacao grupo, String titulo, String texto, String link) {
}
