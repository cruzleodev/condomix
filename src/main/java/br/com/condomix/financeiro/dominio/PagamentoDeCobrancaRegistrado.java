package br.com.condomix.financeiro.dominio;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Publicado ao registrar o pagamento de uma cobrança. O caixa reage criando as receitas.
 *
 * @param peloGateway pago por boleto ou Pix do gateway (o dinheiro entra na conta do gateway)
 * @param tarifa      o que o gateway cobrou (vira despesa); vazio na baixa manual
 */
public record PagamentoDeCobrancaRegistrado(UUID cobrancaId, boolean peloGateway, BigDecimal tarifa) {

	public static PagamentoDeCobrancaRegistrado manual(UUID cobrancaId) {
		return new PagamentoDeCobrancaRegistrado(cobrancaId, false, null);
	}

}
