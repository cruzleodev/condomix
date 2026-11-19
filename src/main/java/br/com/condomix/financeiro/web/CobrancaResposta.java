package br.com.condomix.financeiro.web;

import br.com.condomix.financeiro.aplicacao.ConsultaDeCobrancas.CobrancaHoje;
import br.com.condomix.financeiro.dominio.Cobranca;
import br.com.condomix.financeiro.dominio.FormaDePagamento;
import br.com.condomix.financeiro.dominio.ItemDeCobranca;
import br.com.condomix.financeiro.dominio.OrigemCobranca;
import br.com.condomix.financeiro.dominio.SituacaoDoBoleto;
import br.com.condomix.financeiro.dominio.StatusCobranca;
import br.com.condomix.pagamentos.TipoDeGateway;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Uma cobrança como a API devolve.
 *
 * @param valorAtualizado em aberto: com multa e juros até hoje; paga: com os encargos até a data do pagamento
 */
record CobrancaResposta(
		UUID id,
		UUID unidadeId,
		String unidade,
		OrigemCobranca origem,
		YearMonth competencia,
		String descricao,
		LocalDate vencimento,
		BigDecimal valor,
		List<ItemDeCobranca> itens,
		StatusCobranca status,
		boolean vencida,
		long diasDeAtraso,
		BigDecimal multa,
		BigDecimal juros,
		BigDecimal valorAtualizado,
		LocalDate pagoEm,
		BigDecimal valorPago,
		FormaDePagamento formaPagamento,
		String motivoCancelamento,
		BoletoDaCobranca boleto) {

	/**
	 * Só a situação do boleto (os dados para pagar vêm em {@code /pagamento}, para a lista ficar leve).
	 *
	 * @param erro por que a emissão falhou, para o síndico corrigir
	 */
	record BoletoDaCobranca(SituacaoDoBoleto situacao, TipoDeGateway gateway, String erro) {
	}

	static CobrancaResposta de(CobrancaHoje h) {
		Cobranca c = h.cobranca();
		var e = h.encargos();
		return new CobrancaResposta(c.getId(), c.getUnidadeId(), h.unidade(), c.getOrigem(), c.getCompetencia(),
				c.getDescricao(), c.getVencimento(), c.getValor(), c.getItens(), c.getStatus(), h.vencida(),
				e == null ? 0 : e.diasDeAtraso(), e == null ? null : e.multa(), e == null ? null : e.juros(),
				e == null ? null : e.total(), c.getPagoEm(), c.getValorPago(), c.getFormaPagamento(), c.getMotivoCancelamento(),
				h.boleto() == null ? null : new BoletoDaCobranca(h.boleto().getSituacao(), h.boleto().getGateway(), h.boleto().getErro()));
	}

}
