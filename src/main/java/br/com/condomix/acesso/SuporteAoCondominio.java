package br.com.condomix.acesso;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Modo suporte: o administrador da plataforma recebe um token temporário, só de leitura, para um
 * condomínio. Cada abertura fica registrada com o motivo (LGPD). Usado pelo módulo de administração.
 */
public interface SuporteAoCondominio {

	TokenDeSuporte abrir(UUID administradorId, UUID condominioId, String motivo);

	/** Os acessos mais recentes, para auditoria. */
	List<RegistroDeSuporte> historico();

	record TokenDeSuporte(UUID acessoId, String token, Instant expiraEm) {
	}

	record RegistroDeSuporte(UUID id, UUID administradorId, String administradorEmail, UUID condominioId,
			String motivo, Instant inicio, Instant expiraEm) {
	}

}
