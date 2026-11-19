package br.com.condomix.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** O que o módulo de reservas precisa do financeiro (SOLID: I): o aluguel do espaço e a inadimplência da unidade. */
public interface CobrancasParaReservas {

	/** A unidade tem cobrança em aberto vencida antes de {@code hoje}? */
	boolean unidadeInadimplente(UUID unidadeId, LocalDate hoje);

	/**
	 * Lança o aluguel como cobrança avulsa ("Aluguel de espaço") na unidade, na mesma transação da reserva.
	 * Com o boleto ligado, sai com boleto e Pix. Devolve o id da cobrança.
	 */
	UUID cobrarAluguel(UUID unidadeId, String descricao, BigDecimal valor, LocalDate vencimento, UUID criadaPor);

	/** Cancela o aluguel de uma reserva cancelada no prazo. Já pago, não mexe (o síndico combina a devolução). */
	ResultadoDoCancelamento cancelarAluguel(UUID cobrancaId, String motivo);

	enum ResultadoDoCancelamento {
		CANCELADA,
		/** Já estava paga: fica como está. */
		JA_PAGA,
		/** Já estava cancelada (ex.: pelo síndico, na tela de cobranças). */
		JA_CANCELADA
	}

}
