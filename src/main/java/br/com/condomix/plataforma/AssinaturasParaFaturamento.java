package br.com.condomix.plataforma;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * O que o faturamento (a cobrança da assinatura do Condomix) precisa da plataforma: quem paga, quanto, e
 * suspender ou reativar o acesso pela falta de pagamento. Só dados da plataforma.
 */
public interface AssinaturasParaFaturamento {

	/** Os testes grátis que chegaram ao fim viram assinaturas ativas (e entram no histórico). Devolve quantos. */
	int encerrarTestesVencidos(LocalDate hoje);

	/** Quem paga hoje: condomínios não suspensos, com o plano base em situação ativa. */
	List<AssinaturaCobravel> cobraveis();

	/** O plano e a situação do condomínio, para a tela do síndico. Vazio sem assinatura vigente. */
	Optional<AssinaturaDoCliente> doCondominio(UUID condominioId);

	/** Quem aparece no boleto: o próprio condomínio. */
	PagadorDaAssinatura pagador(UUID condominioId);

	/** Suspende o acesso, se ainda estiver ativo. Fica no histórico como feito pelo sistema. */
	void suspenderPorFaltaDePagamento(UUID condominioId);

	/** Reativa o acesso, mas só se a suspensão foi por falta de pagamento (a manual continua). */
	void reativarAposPagamento(UUID condominioId);

	/** @param preco vazio se o administrador ainda não definiu o preço do plano nesta periodicidade */
	record AssinaturaCobravel(UUID condominioId, UUID planoId, String nomeDoPlano, Periodicidade periodicidade,
			BigDecimal preco) {
	}

	record AssinaturaDoCliente(String nomeDoPlano, Periodicidade periodicidade, StatusAssinatura status,
			LocalDate fimDoTeste, BigDecimal preco, boolean ativo, MotivoDaSuspensao motivoSuspensao) {
	}

	/** @param cnpj só os caracteres, sem pontuação; vazio se não foi cadastrado */
	record PagadorDaAssinatura(UUID condominioId, String nome, String cnpj, String email) {
	}

}
