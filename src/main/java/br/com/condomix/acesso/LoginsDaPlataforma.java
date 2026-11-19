package br.com.condomix.acesso;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Os logins vistos pelo administrador da plataforma: busca por e-mail, bloqueio e desbloqueio.
 * Mostra só dados de acesso (e-mail, situação, em que condomínios entra e com que papel), nunca dados
 * internos dos condomínios. Usado pelo módulo de administração.
 */
public interface LoginsDaPlataforma {

	/** Logins cujo e-mail contém o termo (até 20). */
	List<LoginNaPlataforma> buscar(String parteDoEmail);

	/** Bloqueia o login em todos os condomínios. Os tokens dele deixam de valer na hora. */
	LoginNaPlataforma bloquear(UUID usuarioId, String motivo, UUID bloqueadoPor);

	LoginNaPlataforma desbloquear(UUID usuarioId);

	/** E-mail de cada login, para mostrar quem fez cada coisa. Ids inexistentes ficam de fora. */
	Map<UUID, String> emails(Collection<UUID> usuarioIds);

	record LoginNaPlataforma(UUID id, String email, boolean bloqueado, String motivoDoBloqueio, Instant bloqueadoEm,
			boolean administradorDaPlataforma, Instant ultimoAcesso, List<AcessoDoLogin> condominios) {
	}

	/** Um condomínio ativo em que o login entra hoje, com os papéis dele lá. */
	record AcessoDoLogin(UUID condominioId, String nome, Set<Perfil> perfis) {
	}

}
