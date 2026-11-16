package br.com.condomix.acesso;

import java.time.Instant;
import java.util.UUID;

/**
 * Um convite recém-criado. O {@code link} leva ao app, onde a pessoa define a senha.
 * Enquanto não há envio de e-mail (etapa de notificações), quem convidou repassa o link.
 */
public record ConviteGerado(UUID conviteId, String email, String link, Instant expiraEm) {
}
