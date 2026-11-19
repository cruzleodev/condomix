package br.com.condomix.financeiro.asaas;

import br.com.condomix.financeiro.AmbienteDoGateway;
import br.com.condomix.financeiro.gateway.GatewayDePagamento;
import br.com.condomix.financeiro.gateway.GatewayDePagamento.Credencial;
import br.com.condomix.financeiro.gateway.GatewayDePagamento.PedidoDeBoleto;
import br.com.condomix.financeiro.gateway.GatewayDePagamentoContrato;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * O Asaas cumpre o contrato. A API do Asaas é simulada com as respostas da documentação (v3), conferindo
 * também o que o Condomix envia: a chave no cabeçalho, multa e juros, e o id da cobrança como referência.
 */
class GatewayAsaasContratoTests extends GatewayDePagamentoContrato {

	private static final String API = "https://api-sandbox.asaas.com/v3";

	private final RestClient.Builder construtor = RestClient.builder();

	private final MockRestServiceServer asaas = MockRestServiceServer.bindTo(construtor).ignoreExpectOrder(true).build();

	private final GatewayAsaas gateway = new GatewayAsaas(construtor, new PropriedadesDoAsaas(null, null, null, null));

	@AfterEach
	void conferirChamadas() {
		asaas.verify();
	}

	@Override
	protected GatewayDePagamento gateway() {
		return gateway;
	}

	@Override
	protected Credencial credencialValida() {
		return new Credencial(AmbienteDoGateway.SANDBOX, "chave-boa");
	}

	@Override
	protected Credencial credencialRecusada() {
		return new Credencial(AmbienteDoGateway.SANDBOX, "chave-ruim");
	}

	/** Cada passo do contrato começa uma rodada nova: o que foi preparado antes já foi usado. */
	private void novaRodada() {
		asaas.verify();
		asaas.reset();
	}

	@Override
	protected void aceitarChave(Credencial credencial) {
		novaRodada();
		asaas.expect(requestTo(API + "/finance/balance"))
				.andExpect(header("access_token", credencial.chave()))
				.andRespond(withSuccess("{\"balance\": 0}", MediaType.APPLICATION_JSON));
	}

	@Override
	protected void recusarChave(Credencial credencial) {
		novaRodada();
		asaas.expect(requestTo(API + "/finance/balance"))
				.andExpect(header("access_token", credencial.chave()))
				.andRespond(withStatus(HttpStatus.UNAUTHORIZED));
	}

	@Override
	protected void aceitarEmissao(PedidoDeBoleto pedido, String id) {
		novaRodada();
		asaas.expect(requestTo(API + "/customers?externalReference=" + pedido.pagador().pessoaId() + "&limit=1"))
				.andRespond(withSuccess("{\"data\": [], \"totalCount\": 0}", MediaType.APPLICATION_JSON));
		asaas.expect(requestTo(API + "/customers")).andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.cpfCnpj").value(pedido.pagador().documento()))
				.andExpect(jsonPath("$.notificationDisabled").value(true))
				.andRespond(withSuccess("{\"id\": \"cus_000001\"}", MediaType.APPLICATION_JSON));
		asaas.expect(requestTo(API + "/payments")).andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.customer").value("cus_000001"))
				.andExpect(jsonPath("$.billingType").value("BOLETO"))
				.andExpect(jsonPath("$.dueDate").value(pedido.vencimento().toString()))
				.andExpect(jsonPath("$.value").value(350.00))
				.andExpect(jsonPath("$.fine.value").value(2.00))
				.andExpect(jsonPath("$.interest.value").value(1.00))
				.andExpect(jsonPath("$.externalReference").value(pedido.referencia().toString()))
				.andRespond(withSuccess("""
						{"id": "%s", "status": "PENDING", "value": 350.00, "bankSlipUrl": "https://sandbox.asaas.com/b/pdf/%s"}"""
						.formatted(id, id), MediaType.APPLICATION_JSON));
		asaas.expect(requestTo(API + "/payments/" + id + "/identificationField"))
				.andRespond(withSuccess("{\"identificationField\": \"00190000090123456700400000000000184260000035000\"}",
						MediaType.APPLICATION_JSON));
		asaas.expect(requestTo(API + "/payments/" + id + "/pixQrCode"))
				.andRespond(withSuccess("{\"encodedImage\": \"iVBORw0KGgo=\", \"payload\": \"00020101021226820014br.gov.bcb.pix\"}",
						MediaType.APPLICATION_JSON));
	}

	@Override
	protected void recusarPagadorSemDocumento(PedidoDeBoleto pedido) {
		novaRodada();
		asaas.expect(requestTo(API + "/customers?externalReference=" + pedido.pagador().pessoaId() + "&limit=1"))
				.andRespond(withSuccess("{\"data\": []}", MediaType.APPLICATION_JSON));
		asaas.expect(requestTo(API + "/customers")).andExpect(method(HttpMethod.POST))
				.andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("""
						{"errors": [{"code": "invalid_cpfCnpj", "description": "O CPF/CNPJ do cliente é obrigatório."}]}"""));
	}

	@Override
	protected void aindaNaoPago(String id) {
		novaRodada();
		asaas.expect(requestTo(API + "/payments/" + id))
				.andRespond(withSuccess("{\"id\": \"%s\", \"status\": \"PENDING\", \"value\": 350.00, \"netValue\": 348.01}"
						.formatted(id), MediaType.APPLICATION_JSON));
	}

	@Override
	protected void pago(String id, LocalDate data, BigDecimal valor, BigDecimal tarifa) {
		novaRodada();
		asaas.expect(requestTo(API + "/payments/" + id))
				.andRespond(withSuccess("""
						{"id": "%s", "status": "RECEIVED", "billingType": "PIX", "value": %s, "netValue": %s,
						 "paymentDate": "%s", "clientPaymentDate": "%s"}"""
						.formatted(id, valor, valor.subtract(tarifa), data, data), MediaType.APPLICATION_JSON));
	}

	@Override
	protected void inexistente(String id) {
		novaRodada();
		asaas.expect(requestTo(API + "/payments/" + id)).andExpect(method(HttpMethod.DELETE))
				.andRespond(withStatus(HttpStatus.NOT_FOUND));
	}

	@Test
	void reaproveitaOClienteDoPagadorEEmiteSemPixQuandoAContaNaoTemChave() {
		PedidoDeBoleto pedido = pedido("52998224725");
		asaas.expect(requestTo(API + "/customers?externalReference=" + pedido.pagador().pessoaId() + "&limit=1"))
				.andRespond(withSuccess("{\"data\": [{\"id\": \"cus_existente\"}]}", MediaType.APPLICATION_JSON));
		asaas.expect(requestTo(API + "/payments")).andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.customer").value("cus_existente"))
				.andRespond(withSuccess("{\"id\": \"pay_sem_pix\", \"bankSlipUrl\": \"https://boleto\"}", MediaType.APPLICATION_JSON));
		asaas.expect(requestTo(API + "/payments/pay_sem_pix/identificationField"))
				.andRespond(withSuccess("{\"identificationField\": \"123\"}", MediaType.APPLICATION_JSON));
		asaas.expect(requestTo(API + "/payments/pay_sem_pix/pixQrCode"))
				.andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
						.body("{\"errors\": [{\"description\": \"Não há chave Pix cadastrada.\"}]}"));

		var emitido = gateway.emitir(credencialValida(), pedido);

		assertThat(emitido.idNoGateway()).isEqualTo("pay_sem_pix");
		assertThat(emitido.url()).isEqualTo("https://boleto");
		assertThat(emitido.pixCopiaECola()).isNull();
	}

	@Test
	void foraDoArAvisaParaTentarDeNovo() {
		asaas.expect(requestTo(API + "/payments/pay_x")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
		try {
			gateway.pagamento(new Credencial(AmbienteDoGateway.SANDBOX, "chave-boa"), "pay_x");
		}
		catch (br.com.condomix.financeiro.gateway.FalhaNoGateway e) {
			assertThat(e.getMessage()).contains("Tente de novo");
			return;
		}
		throw new AssertionError("deveria ter falhado");
	}

}
