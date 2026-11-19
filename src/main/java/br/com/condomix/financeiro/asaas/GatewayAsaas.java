package br.com.condomix.financeiro.asaas;

import br.com.condomix.financeiro.AmbienteDoGateway;
import br.com.condomix.financeiro.TipoDeGateway;
import br.com.condomix.financeiro.gateway.FalhaNoGateway;
import br.com.condomix.financeiro.gateway.GatewayDePagamento;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * O gateway Asaas (API v3). Cada condomínio usa a chave de API da própria conta, então o dinheiro do boleto
 * e do Pix cai direto na conta do condomínio. Documentação: https://docs.asaas.com
 *
 * <p>O boleto é emitido com {@code billingType=BOLETO}: quando a conta tem chave Pix, o Asaas inclui o QR Code
 * do Pix no mesmo boleto. Multa e juros vão configurados, então o boleto continua pagável depois do vencimento.
 */
@Component
@EnableConfigurationProperties(PropriedadesDoAsaas.class)
public class GatewayAsaas implements GatewayDePagamento {

	/** Situações do Asaas em que o dinheiro foi recebido. */
	private static final Set<String> PAGO = Set.of("RECEIVED", "CONFIRMED", "RECEIVED_IN_CASH");

	private static final Map<String, FormaDoPagamento> FORMAS = Map.of("BOLETO", FormaDoPagamento.BOLETO, "PIX", FormaDoPagamento.PIX);

	private final RestClient.Builder construtor;

	private final PropriedadesDoAsaas propriedades;

	@Autowired
	public GatewayAsaas(PropriedadesDoAsaas propriedades) {
		this(RestClient.builder().requestFactory(fabrica(propriedades)), propriedades);
	}

	/** Para os testes, com um servidor de mentira ligado ao construtor. */
	GatewayAsaas(RestClient.Builder construtor, PropriedadesDoAsaas propriedades) {
		this.construtor = construtor;
		this.propriedades = propriedades;
	}

	@Override
	public TipoDeGateway tipo() {
		return TipoDeGateway.ASAAS;
	}

	@Override
	public void validar(Credencial credencial) {
		chamar(() -> cliente(credencial).get().uri("/finance/balance").retrieve().toBodilessEntity());
	}

	@Override
	public BoletoEmitido emitir(Credencial credencial, PedidoDeBoleto pedido) {
		RestClient api = cliente(credencial);
		String cliente = clienteDoPagador(api, pedido.pagador());
		var corpo = new NovoPagamento(cliente, "BOLETO", pedido.valor(), pedido.vencimento(),
				limitar(pedido.descricao(), 500), pedido.referencia().toString(),
				new Percentual(pedido.multaPercentual()), new Percentual(pedido.jurosMensalPercentual()));
		Pagamento criado = chamar(() -> api.post().uri("/payments").contentType(MediaType.APPLICATION_JSON).body(corpo)
				.retrieve().body(Pagamento.class));
		LinhaDigitavel linha = chamar(() -> api.get().uri("/payments/{id}/identificationField", criado.id())
				.retrieve().body(LinhaDigitavel.class));
		PixDoPagamento pix = pix(api, criado.id());
		return new BoletoEmitido(criado.id(), criado.bankSlipUrl() != null ? criado.bankSlipUrl() : criado.invoiceUrl(),
				linha == null ? null : linha.identificationField(), pix == null ? null : pix.payload(),
				pix == null ? null : pix.encodedImage());
	}

	@Override
	public void cancelar(Credencial credencial, String idNoGateway) {
		RestClient api = cliente(credencial);
		chamar(() -> api.delete().uri("/payments/{id}", idNoGateway).retrieve()
				.onStatus(s -> s.value() == HttpStatus.NOT_FOUND.value(), (req, resp) -> { }) // já não existe: tudo bem
				.toBodilessEntity());
	}

	@Override
	public Optional<PagamentoConfirmado> pagamento(Credencial credencial, String idNoGateway) {
		Pagamento p = chamar(() -> cliente(credencial).get().uri("/payments/{id}", idNoGateway).retrieve().body(Pagamento.class));
		if (p == null || !PAGO.contains(p.status())) {
			return Optional.empty();
		}
		LocalDate data = p.paymentDate() != null ? p.paymentDate() : p.clientPaymentDate();
		BigDecimal tarifa = p.netValue() == null ? BigDecimal.ZERO : p.value().subtract(p.netValue()).max(BigDecimal.ZERO);
		return Optional.of(new PagamentoConfirmado(data, p.value(), tarifa,
				FORMAS.getOrDefault(p.billingType(), FormaDoPagamento.OUTRA)));
	}

	/** O cliente do Asaas com {@code externalReference} = id da pessoa; cria na primeira cobrança. */
	private String clienteDoPagador(RestClient api, Pagador pagador) {
		ListaDeClientes existentes = chamar(() -> api.get()
				.uri(u -> u.path("/customers").queryParam("externalReference", pagador.pessoaId()).queryParam("limit", 1).build())
				.retrieve().body(ListaDeClientes.class));
		if (existentes != null && existentes.data() != null && !existentes.data().isEmpty()) {
			return existentes.data().getFirst().id();
		}
		var novo = new NovoCliente(limitar(pagador.nome(), 100), pagador.documento(), pagador.email(),
				pagador.pessoaId().toString(), true);
		return chamar(() -> api.post().uri("/customers").contentType(MediaType.APPLICATION_JSON).body(novo)
				.retrieve().body(Cliente.class)).id();
	}

	/** Sem chave Pix na conta, o Asaas recusa o QR Code: o boleto sai só com a linha digitável. */
	private PixDoPagamento pix(RestClient api, String id) {
		try {
			return api.get().uri("/payments/{id}/pixQrCode", id).retrieve()
					.onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> { throw new SemPix(); })
					.body(PixDoPagamento.class);
		}
		catch (SemPix e) {
			return null;
		}
		catch (RestClientException e) {
			return null; // o boleto já existe; sem o Pix ele continua valendo
		}
	}

	private RestClient cliente(Credencial credencial) {
		String base = credencial.ambiente() == AmbienteDoGateway.PRODUCAO ? propriedades.urlProducao() : propriedades.urlSandbox();
		return construtor.clone()
				.baseUrl(base)
				.defaultHeader("access_token", credencial.chave())
				.defaultHeader("User-Agent", "Condomix")
				.defaultStatusHandler(HttpStatusCode::isError, GatewayAsaas::recusa)
				.build();
	}

	/** Transforma a resposta de erro do Asaas numa mensagem para o síndico. */
	private static void recusa(org.springframework.http.HttpRequest pedido, ClientHttpResponse resposta) throws IOException {
		int status = resposta.getStatusCode().value();
		if (status == 401) {
			throw new FalhaNoGateway("A chave de API não foi aceita pelo Asaas. Confira a chave da conta do condomínio.");
		}
		String corpo = new String(resposta.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		String descricao = ErrosDoAsaas.primeiraDescricao(corpo);
		if (status >= 500) {
			throw new FalhaNoGateway("O Asaas está com problemas agora. Tente de novo em alguns minutos.");
		}
		throw new FalhaNoGateway(descricao != null ? "O Asaas recusou: " + descricao : "O Asaas recusou o pedido (erro " + status + ")");
	}

	private static <T> T chamar(java.util.function.Supplier<T> chamada) {
		try {
			return chamada.get();
		}
		catch (FalhaNoGateway e) {
			throw e;
		}
		catch (RestClientException e) {
			if (e.getCause() instanceof FalhaNoGateway falha) {
				throw falha;
			}
			throw new FalhaNoGateway("Não foi possível falar com o Asaas. Tente de novo em alguns minutos.", e);
		}
	}

	private static JdkClientHttpRequestFactory fabrica(PropriedadesDoAsaas propriedades) {
		var fabrica = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(propriedades.tempoDeConexao()).build());
		fabrica.setReadTimeout(propriedades.tempoDeResposta());
		return fabrica;
	}

	private static String limitar(String texto, int tamanho) {
		return texto == null || texto.length() <= tamanho ? texto : texto.substring(0, tamanho);
	}

	private static final class SemPix extends RuntimeException {
	}

	// ----- formato da API do Asaas -----

	record NovoCliente(String name, String cpfCnpj, String email, String externalReference, boolean notificationDisabled) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Cliente(String id) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ListaDeClientes(List<Cliente> data) {
	}

	record Percentual(BigDecimal value) {
	}

	record NovoPagamento(String customer, String billingType, BigDecimal value, LocalDate dueDate, String description,
			String externalReference, Percentual fine, Percentual interest) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Pagamento(String id, String status, BigDecimal value, BigDecimal netValue, String billingType,
			LocalDate paymentDate, LocalDate clientPaymentDate, String bankSlipUrl, String invoiceUrl) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record LinhaDigitavel(String identificationField) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record PixDoPagamento(String encodedImage, String payload) {
	}

}
