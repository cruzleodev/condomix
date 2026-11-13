package br.com.condomix.acesso;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Resposta do login e da troca de condomínio.
 *
 * @param token        o JWT, para enviar em {@code Authorization: Bearer <token>}
 * @param tipo         sempre "Bearer"
 * @param expiraEm     quando o token deixa de valer
 * @param condominioId condomínio selecionado; {@code null} quando o usuário tem mais de um e ainda precisa escolher
 * @param perfis       perfis no condomínio selecionado
 * @param condominios  todos os condomínios que o usuário pode acessar
 */
public record Sessao(
		String token,
		String tipo,
		Instant expiraEm,
		UUID condominioId,
		Set<Perfil> perfis,
		List<CondominioDisponivel> condominios) {

	public record CondominioDisponivel(UUID id, String nome, Set<Perfil> perfis) {
	}

}
