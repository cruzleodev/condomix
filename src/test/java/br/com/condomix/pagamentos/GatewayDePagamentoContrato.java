package br.com.condomix.pagamentos;

import br.com.condomix.pagamentos.GatewayDePagamento.BoletoEmitido;
import br.com.condomix.pagamentos.GatewayDePagamento.Credencial;
import br.com.condomix.pagamentos.GatewayDePagamento.Pagador;
import br.com.condomix.pagamentos.GatewayDePagamento.PedidoDeBoleto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste de contrato (SOLID: L): todo {@link GatewayDePagamento} tem que se comportar igual para o Condomix.
 * Cada implementação estende esta classe e prepara o "outro lado" (a API de verdade gravada, ou a memória).
 * Os testes sempre preparam e logo em seguida chamam, um passo de cada vez.
 */
public abstract class GatewayDePagamentoContrato {

	protected static final LocalDate HOJE = LocalDate.of(2026, 11, 19);

	protected abstract GatewayDePagamento gateway();

	protected abstract Credencial credencialValida();

	protected abstract Credencial credencialRecusada();

	/** Prepara o gateway para aceitar (ou recusar) a chave na próxima chamada. */
	protected abstract void aceitarChave(Credencial credencial);

	protected abstract void recusarChave(Credencial credencial);

	/** Prepara o gateway para aceitar a emissão do pedido e devolver o id informado. */
	protected abstract void aceitarEmissao(PedidoDeBoleto pedido, String idEsperado);

	/** Prepara o gateway para recusar a emissão de quem não tem CPF/CNPJ. */
	protected abstract void recusarPagadorSemDocumento(PedidoDeBoleto pedido);

	/** O boleto foi emitido, mas ainda não foi pago. */
	protected abstract void aindaNaoPago(String id);

	/** O morador pagou {@code valor}; o gateway cobrou {@code tarifa}. */
	protected abstract void pago(String id, LocalDate data, BigDecimal valor, BigDecimal tarifa);

	/** O boleto não existe mais no gateway. */
	protected abstract void inexistente(String id);

	protected PedidoDeBoleto pedido(String documento) {
		return new PedidoDeBoleto(UUID.randomUUID(), new Pagador(UUID.randomUUID(), "Ana Pagadora", documento, "ana@teste.com"),
				new BigDecimal("350.00"), HOJE.plusDays(20), "Taxa condominial 12/2026", new BigDecimal("2.00"), new BigDecimal("1.00"));
	}

	@Test
	void validaAChave() {
		aceitarChave(credencialValida());
		assertThatCode(() -> gateway().validar(credencialValida())).doesNotThrowAnyException();
		recusarChave(credencialRecusada());
		assertThatThrownBy(() -> gateway().validar(credencialRecusada())).isInstanceOf(FalhaNoGateway.class);
	}

	@Test
	void emiteOBoletoComLinhaDigitavelEPix() {
		PedidoDeBoleto pedido = pedido("52998224725");
		aceitarEmissao(pedido, "pay_contrato_1");

		BoletoEmitido emitido = gateway().emitir(credencialValida(), pedido);

		assertThat(emitido.idNoGateway()).isNotBlank();
		assertThat(emitido.linhaDigitavel()).isNotBlank();
		assertThat(emitido.pixCopiaECola()).isNotBlank();
	}

	@Test
	void recusaPagadorSemDocumento() {
		PedidoDeBoleto pedido = pedido(null);
		recusarPagadorSemDocumento(pedido);
		assertThatThrownBy(() -> gateway().emitir(credencialValida(), pedido))
				.isInstanceOf(FalhaNoGateway.class)
				.hasMessageContaining("CPF");
	}

	@Test
	void informaOPagamentoSoDepoisDePago() {
		PedidoDeBoleto pedido = pedido("52998224725");
		aceitarEmissao(pedido, "pay_contrato_2");
		String id = gateway().emitir(credencialValida(), pedido).idNoGateway();

		aindaNaoPago(id);
		assertThat(gateway().pagamento(credencialValida(), id)).isEmpty();

		pago(id, HOJE, new BigDecimal("357.00"), new BigDecimal("1.99"));
		var pagamento = gateway().pagamento(credencialValida(), id).orElseThrow();
		assertThat(pagamento.data()).isEqualTo(HOJE);
		assertThat(pagamento.valorPago()).isEqualByComparingTo("357.00");
		assertThat(pagamento.tarifa()).isEqualByComparingTo("1.99");
		assertThat(pagamento.forma()).isEqualTo(GatewayDePagamento.FormaDoPagamento.PIX);
	}

	@Test
	void cancelarOQueNaoExisteNaoEErro() {
		inexistente("pay_que_nao_existe");
		assertThatCode(() -> gateway().cancelar(credencialValida(), "pay_que_nao_existe")).doesNotThrowAnyException();
	}

}
