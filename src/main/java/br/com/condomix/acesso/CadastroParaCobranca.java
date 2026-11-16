package br.com.condomix.acesso;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * O que o módulo financeiro precisa do cadastro do condomínio atual (SOLID: I). Só leitura.
 */
public interface CadastroParaCobranca {

	/** Unidades ativas, com a fração ideal e quem paga a cobrança na data. */
	List<UnidadeCobravel> unidadesAtivas(LocalDate data);

	/** Mandatos com desconto na taxa que valeram em algum dia do período. */
	List<BeneficioDeMandato> beneficiosNoPeriodo(LocalDate inicio, LocalDate fim);

	/** Unidades cujas cobranças o usuário logado pode ver ("meus débitos"): ele é proprietário ou inquilino. */
	Set<UUID> unidadesDoPagador(UUID usuarioId, LocalDate data);

	/**
	 * @param responsavel quem paga: o proprietário; sem proprietário, o inquilino; sem os dois, o morador.
	 *                    Vazio quando ninguém está ligado à unidade.
	 */
	record UnidadeCobravel(UUID id, String descricao, BigDecimal fracaoIdeal, Responsavel responsavel) {
	}

	record Responsavel(UUID pessoaId, String nome, String documento) {
	}

	/** @param fim último dia do mandato; vazio enquanto não foi encerrado (vigente ou prorrogado) */
	record BeneficioDeMandato(UUID unidadeId, BigDecimal percentual, LocalDate inicio, LocalDate fim, Cargo cargo) {
	}

}
