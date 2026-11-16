package br.com.condomix.acesso;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Entrada de um condomínio novo: registra o primeiro síndico (pessoa + mandato) e gera o convite
 * para ele criar o login. Usado pelo módulo de administração da plataforma.
 */
public interface IngressoDeSindico {

	ConviteGerado registrar(UUID condominioId, NovoSindico sindico, UUID registradoPor);

	/** @param documento CPF ou CNPJ (só dígitos), opcional */
	record NovoSindico(String nome, String email, String documento, LocalDate inicioMandato, LocalDate fimMandato) {
	}

}
