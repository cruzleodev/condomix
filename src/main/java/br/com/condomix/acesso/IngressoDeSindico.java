package br.com.condomix.acesso;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * O síndico de um cliente, visto pelo administrador da plataforma: o primeiro síndico de um condomínio
 * novo, a troca de síndico e um novo convite quando o link se perdeu. Usado pelo módulo de administração.
 */
public interface IngressoDeSindico {

	/**
	 * Registra o síndico (pessoa + mandato) e gera o convite para ele criar o login. O mandato anterior,
	 * se houver, termina na véspera da posse. Se a pessoa já tem login, não há convite: o acesso de síndico
	 * vem do mandato.
	 */
	SindicoRegistrado registrar(UUID condominioId, NovoSindico sindico, UUID registradoPor);

	/** O síndico de hoje (ou o próximo, se o mandato ainda não começou). */
	Optional<SindicoAtual> sindicoAtual(UUID condominioId);

	/**
	 * Novo convite para o síndico atual, que ainda não criou o login. O convite anterior deixa de valer.
	 * @param email para corrigir o e-mail; vazio usa o do cadastro
	 */
	ConviteGerado reconvidar(UUID condominioId, String email, UUID registradoPor);

	/** @param documento CPF ou CNPJ (só dígitos), opcional */
	record NovoSindico(String nome, String email, String documento, LocalDate inicioMandato, LocalDate fimMandato) {
	}

	/** {@code convite} vazio quando a pessoa já tinha login. */
	record SindicoRegistrado(SindicoAtual sindico, ConviteGerado convite) {
	}

	/** {@code convitePendenteAte}: até quando vale o convite ainda não usado (vazio se não há). */
	record SindicoAtual(String nome, String email, LocalDate inicioMandato, LocalDate fimMandato, boolean temLogin,
			Instant convitePendenteAte) {
	}

}
