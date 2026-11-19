package br.com.condomix.plataforma;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Gestão dos clientes pelo administrador da plataforma: lista, suspensão e assinatura.
 * Só dados da plataforma (condomínio, plano, assinatura), nunca dados internos dos condomínios.
 */
public interface PainelDeCondominios {

	List<CondominioNaPlataforma> listar();

	CondominioNaPlataforma obter(UUID condominioId);

	/** @param feitoPor quem suspendeu (vai para o histórico) */
	void suspender(UUID condominioId, UUID feitoPor);

	void reativar(UUID condominioId, UUID feitoPor);

	/**
	 * Muda o plano base, a periodicidade ou a situação. Trocar plano ou periodicidade encerra a
	 * assinatura atual (fica no histórico) e abre uma nova; mudar só a situação altera a atual.
	 */
	CondominioNaPlataforma alterarAssinatura(UUID condominioId, String codigoPlano, Periodicidade periodicidade,
			StatusAssinatura status, UUID feitoPor);

	/** Cada mudança na assinatura e no acesso do condomínio, mais recentes primeiro. */
	List<EventoDaAssinatura> historico(UUID condominioId);

	record CondominioNaPlataforma(UUID id, String nome, TipoCondominio tipo, String cnpj, boolean ativo,
			Instant criadoEm, AssinaturaAtual assinatura) {
	}

	/** Plano, periodicidade e situação ficam vazios na suspensão e na reativação. {@code feitoPor} vazio: o sistema. */
	record EventoDaAssinatura(Instant momento, TipoDeEventoDaAssinatura tipo, String plano, String nomeDoPlano,
			Periodicidade periodicidade, StatusAssinatura status, UUID feitoPor) {
	}

	/** {@code null} no condomínio quando não há assinatura de plano base vigente. */
	record AssinaturaAtual(String plano, String nomeDoPlano, Periodicidade periodicidade, StatusAssinatura status,
			LocalDate inicio) {
	}

}
