package br.com.condomix.acesso;

import java.time.Instant;
import java.util.UUID;

/**
 * Um convite recém-criado. O {@code link} leva ao app, onde a pessoa define a senha.
 *
 * @param enviadoPorEmail o convite também foi para o e-mail da pessoa (com o e-mail configurado); de todo jeito,
 *                        quem convidou pode repassar o link
 */
public record ConviteGerado(UUID conviteId, String email, String link, Instant expiraEm, boolean enviadoPorEmail) {
}
