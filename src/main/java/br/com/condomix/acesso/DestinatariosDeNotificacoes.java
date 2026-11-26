package br.com.condomix.acesso;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Quem recebe as notificações no condomínio atual (SOLID: I). Só pessoas com login ativo e não bloqueado.
 * Usado pelo módulo de comunicação.
 */
public interface DestinatariosDeNotificacoes {

	/** Quem acompanha os débitos da unidade: proprietários, inquilinos e moradores (o dependente, não). */
	List<Destinatario> quemAcompanhaOsDebitos(UUID unidadeId);

	/** Proprietários, inquilinos e moradores da unidade (quem usa). */
	List<Destinatario> ocupantesDaUnidade(UUID unidadeId);

	/** Síndico (mandato vigente) e administradora. */
	List<Destinatario> daGestao();

	/** Todos com acesso ao condomínio. */
	List<Destinatario> doCondominio();

	Optional<Destinatario> usuario(UUID usuarioId);

	record Destinatario(UUID usuarioId, String email, String nome) {
	}

}
