package br.com.condomix.pagamentos;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Um gateway de pagamentos (Asaas, simulado...). Cada condomínio tem a própria conta no gateway: a credencial
 * vem em cada chamada. Toda implementação passa no mesmo teste de contrato ({@code GatewayDePagamentoContrato}).
 *
 * <p>As chamadas vão pela rede: nunca dentro de uma transação do banco.
 */
public interface GatewayDePagamento {

	TipoDeGateway tipo();

	/** Confere se a chave funciona, ao configurar. Lança {@link FalhaNoGateway} com o motivo se não. */
	void validar(Credencial credencial);

	/** Cria o boleto (com Pix, quando a conta tem chave Pix). */
	BoletoEmitido emitir(Credencial credencial, PedidoDeBoleto pedido);

	/** Cancela um boleto ainda não pago. Cancelar um que não existe mais não é erro. */
	void cancelar(Credencial credencial, String idNoGateway);

	/** O pagamento de um boleto, se já foi pago. Usado para confirmar o aviso (webhook) em vez de confiar nele. */
	Optional<PagamentoConfirmado> pagamento(Credencial credencial, String idNoGateway);

	record Credencial(AmbienteDoGateway ambiente, String chave) {

		@Override
		public String toString() {
			return "Credencial[" + ambiente + ", chave=***]"; // a chave nunca vai para o log
		}

	}

	/**
	 * @param referencia o id da cobrança no Condomix (o gateway devolve no aviso)
	 * @param multaPercentual e {@code jurosMensalPercentual}: o boleto pago depois do vencimento já cobra os encargos
	 */
	record PedidoDeBoleto(UUID referencia, Pagador pagador, BigDecimal valor, LocalDate vencimento, String descricao,
			BigDecimal multaPercentual, BigDecimal jurosMensalPercentual) {
	}

	/** @param referencia quem paga no Condomix (a pessoa ou o condomínio): o gateway reaproveita o mesmo cliente
	 * @param documento CPF ou CNPJ, só dígitos (o gateway exige) */
	record Pagador(UUID referencia, String nome, String documento, String email) {
	}

	/** @param pixQrCode imagem PNG em Base64; vazio se a conta não tem chave Pix */
	record BoletoEmitido(String idNoGateway, String url, String linhaDigitavel, String pixCopiaECola, String pixQrCode) {
	}

	/** @param valorPago com multa e juros, se pagou atrasado; {@code tarifa}: o que o gateway descontou */
	record PagamentoConfirmado(LocalDate data, BigDecimal valorPago, BigDecimal tarifa, FormaDoPagamento forma) {
	}

	enum FormaDoPagamento {
		BOLETO, PIX, OUTRA
	}

}
