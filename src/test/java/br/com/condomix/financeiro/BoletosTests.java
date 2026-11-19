package br.com.condomix.financeiro;

import br.com.condomix.CenarioDeTeste;
import br.com.condomix.TestcontainersConfiguration;
import br.com.condomix.acesso.Perfil;
import br.com.condomix.acesso.Usuario;
import br.com.condomix.financeiro.aplicacao.ProcessadorDeBoletos;
import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.financeiro.aplicacao.RecebimentoPeloGateway;
import br.com.condomix.financeiro.persistencia.BoletoRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Boleto e Pix pelo gateway (simulado): emissão, 2ª via, baixa automática, cancelamento e separação por condomínio. */
@Import({ TestcontainersConfiguration.class, CenarioDeTeste.class })
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BoletosTests {

	private static final String CPF = "52998224725";

	@Autowired MockMvc mvc;
	@Autowired CenarioDeTeste cenario;
	@Autowired ProcessadorDeBoletos processador;
	@Autowired RecebimentoPeloGateway recebimento;
	@Autowired BoletoRepository boletos;

	UUID condominio;
	String sindico;
	String admin;
	String unidade;
	Usuario ana;
	String tokenDoAviso;

	@BeforeEach
	void prepararCondominioComGatewayLigado() throws Exception {
		condominio = cenario.condominio("Residencial dos Boletos");
		sindico = cenario.token(cenario.comPapel(condominio, Perfil.SINDICO));
		admin = cenario.token(cenario.administrador());
		enviar(put("/api/financeiro/configuracao"), sindico, """
				{"tipoRateio": "IGUALITARIO", "diaVencimento": 10, "multaPercentual": 2, "jurosMensalPercentual": 1,
				 "fundoReservaPercentual": 0}""").andExpect(status().isOk());
		unidade = JsonPath.read(enviar(post("/api/unidades"), sindico, """
				{"identificacao": "101", "tipo": "APARTAMENTO"}""").andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.id");
		ana = cenario.usuario("ana");
		cenario.perfil(condominio, ana, Perfil.MORADOR);
		String pessoaAna = cenario.pessoa(condominio, "Ana Proprietária", ana).getId().toString();
		enviar(put("/api/pessoas/{id}", pessoaAna), sindico, """
				{"nome": "Ana Proprietária", "documento": "%s"}""".formatted(CPF)).andExpect(status().isOk());
		vincular(pessoaAna, unidade);
		tokenDoAviso = ligarGateway(condominio);
	}

	@Test
	void avulsaGanhaBoletoComPixEOPagamentoDaBaixaSozinho() throws Exception {
		String cobranca = avulsa(unidade, 200, cenario.hoje().plusDays(10));
		mvc.perform(get("/api/cobrancas/{id}", cobranca).header("Authorization", sindico))
				.andExpect(jsonPath("$.boleto.situacao").value("PENDENTE"));

		processador.processar();

		mvc.perform(get("/api/cobrancas/{id}", cobranca).header("Authorization", sindico))
				.andExpect(jsonPath("$.boleto.situacao").value("EMITIDO"))
				.andExpect(jsonPath("$.boleto.gateway").value("SIMULADO"));
		String moradora = cenario.token(ana);
		mvc.perform(get("/api/eu/cobrancas").header("Authorization", moradora))
				.andExpect(jsonPath("$[0].boleto.situacao").value("EMITIDO"));
		mvc.perform(get("/api/eu/cobrancas/{id}/pagamento", cobranca).header("Authorization", moradora))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.valor").value(200.00))
				.andExpect(jsonPath("$.vencimento").value(cenario.hoje().plusDays(10).toString()))
				.andExpect(jsonPath("$.linhaDigitavel").value(notNullValue()))
				.andExpect(jsonPath("$.pixCopiaECola").value(startsWith("000201")));

		enviar(post("/api/cobrancas/{id}/pagamento-simulado", cobranca), sindico, "{}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.resultado").value("BAIXADA"));

		mvc.perform(get("/api/cobrancas/{id}", cobranca).header("Authorization", sindico))
				.andExpect(jsonPath("$.status").value("PAGA"))
				.andExpect(jsonPath("$.formaPagamento").value("PIX"))
				.andExpect(jsonPath("$.valorPago").value(200.00))
				.andExpect(jsonPath("$.boleto.situacao").value("PAGO"));
		// O dinheiro entra na conta do gateway, com a tarifa como despesa: 200,00 - 1,99.
		mvc.perform(get("/api/financeiro/contas").header("Authorization", sindico))
				.andExpect(jsonPath("$[?(@.tipo == 'GATEWAY')].saldoAtual", hasItem(198.01)));

		// O aviso repetido não dá baixa duas vezes.
		String idNoGateway = idNoGateway(cobranca);
		assertThat(recebimento.confirmar(condominio, TipoDeGateway.SIMULADO, idNoGateway))
				.isEqualTo(RecebimentoPeloGateway.Resultado.JA_BAIXADA);
		mvc.perform(get("/api/financeiro/contas").header("Authorization", sindico))
				.andExpect(jsonPath("$[?(@.tipo == 'GATEWAY')].saldoAtual", hasItem(198.01)));

		// Estornar a baixa automática tira a receita e a tarifa do caixa.
		enviar(post("/api/cobrancas/{id}/estorno", cobranca), sindico, "{}").andExpect(jsonPath("$.status").value("ABERTA"));
		mvc.perform(get("/api/financeiro/contas").header("Authorization", sindico))
				.andExpect(jsonPath("$[?(@.tipo == 'GATEWAY')].saldoAtual", hasItem(0.0)));
	}

	@Test
	void cobrancaVencidaGanhaBoletoComOValorAtualizadoVencendoHoje() throws Exception {
		String cobranca = avulsa(unidade, 200, cenario.hoje().minusDays(30));
		processador.processar();
		// 200 + multa de 2% (4,00) + juros de 1% ao mês por 30 dias (2,00)
		mvc.perform(get("/api/cobrancas/{id}/pagamento", cobranca).header("Authorization", sindico))
				.andExpect(jsonPath("$.valor").value(206.00))
				.andExpect(jsonPath("$.vencimento").value(cenario.hoje().toString()));
	}

	@Test
	void semCpfDoResponsavelFalhaComOMotivoEDepoisTentaDeNovo() throws Exception {
		String outra = JsonPath.read(enviar(post("/api/unidades"), sindico, """
				{"identificacao": "102", "tipo": "APARTAMENTO"}""").andReturn().getResponse().getContentAsString(), "$.id");
		String bruno = JsonPath.read(enviar(post("/api/pessoas"), sindico, """
				{"nome": "Bruno Sem CPF"}""").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		vincular(bruno, outra);
		String cobranca = avulsa(outra, 80, cenario.hoje().plusDays(5));

		processador.processar();
		mvc.perform(get("/api/cobrancas/{id}", cobranca).header("Authorization", sindico))
				.andExpect(jsonPath("$.boleto.situacao").value("FALHOU"))
				.andExpect(jsonPath("$.boleto.erro").value(containsString("CPF ou CNPJ de Bruno Sem CPF")));

		enviar(put("/api/pessoas/{id}", bruno), sindico, """
				{"nome": "Bruno Sem CPF", "documento": "11144477735"}""").andExpect(status().isOk());
		enviar(post("/api/cobrancas/{id}/boleto/nova-tentativa", cobranca), sindico, "{}").andExpect(status().isAccepted());
		processador.processar();
		mvc.perform(get("/api/cobrancas/{id}", cobranca).header("Authorization", sindico))
				.andExpect(jsonPath("$.boleto.situacao").value("EMITIDO"))
				.andExpect(jsonPath("$.boleto.erro").value(nullValue()));
	}

	@Test
	void cancelarOuReceberPorForaCancelaOBoletoNoGateway() throws Exception {
		String cancelada = avulsa(unidade, 50, cenario.hoje().plusDays(5));
		String paga = avulsa(unidade, 60, cenario.hoje().plusDays(5));
		processador.processar();

		enviar(post("/api/cobrancas/{id}/cancelamento", cancelada), sindico, """
				{"motivo": "Lançada por engano"}""").andExpect(jsonPath("$.boleto.situacao").value("A_CANCELAR"));
		enviar(post("/api/cobrancas/{id}/pagamento", paga), sindico, """
				{"forma": "DINHEIRO"}""").andExpect(jsonPath("$.boleto.situacao").value("A_CANCELAR"));
		processador.processar();

		mvc.perform(get("/api/cobrancas/{id}", cancelada).header("Authorization", sindico))
				.andExpect(jsonPath("$.boleto").value(nullValue()));
		mvc.perform(get("/api/eu/cobrancas/{id}/pagamento", paga).header("Authorization", cenario.token(ana)))
				.andExpect(status().isNotFound());
	}

	@Test
	void geracaoDoMesEmiteUmBoletoPorCobranca() throws Exception {
		var mes = YearMonth.from(cenario.hoje()).plusMonths(1);
		enviar(post("/api/financeiro/geracoes"), sindico, """
				{"competencia": "%s", "valorOrdinariaTotal": 900}""".formatted(mes)).andExpect(status().isCreated());
		processador.processar();
		mvc.perform(get("/api/cobrancas").param("competencia", mes.toString()).header("Authorization", sindico))
				.andExpect(jsonPath("$.itens", hasSize(1)))
				.andExpect(jsonPath("$.itens[0].boleto.situacao").value("EMITIDO"));
	}

	@Test
	void semGatewayLigadoNaoHaBoletoAteEmitirOsQueFaltam() throws Exception {
		UUID outro = cenario.condominio("Sem Gateway");
		String sindicoOutro = cenario.token(cenario.comPapel(outro, Perfil.SINDICO));
		String unidadeOutro = JsonPath.read(enviar(post("/api/unidades"), sindicoOutro, """
				{"identificacao": "1", "tipo": "CASA"}""").andReturn().getResponse().getContentAsString(), "$.id");
		String pessoa = JsonPath.read(enviar(post("/api/pessoas"), sindicoOutro, """
				{"nome": "Carla", "documento": "%s"}""".formatted(CPF)).andReturn().getResponse().getContentAsString(), "$.id");
		vincular(pessoa, unidadeOutro, sindicoOutro);
		String cobranca = JsonPath.read(enviar(post("/api/cobrancas"), sindicoOutro, """
				{"unidadeId": "%s", "tipo": "OUTRO", "descricao": "Chave extra", "valor": 30, "vencimento": "%s"}"""
				.formatted(unidadeOutro, cenario.hoje().plusDays(3))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.boleto").value(nullValue()))
				.andReturn().getResponse().getContentAsString(), "$.id");

		mvc.perform(get("/api/financeiro/gateway").header("Authorization", sindicoOutro)).andExpect(jsonPath("$.ligado").value(false));
		enviar(post("/api/cobrancas/boletos"), sindicoOutro, "{}").andExpect(status().isConflict());

		ligarGateway(outro);
		mvc.perform(get("/api/financeiro/gateway").header("Authorization", sindicoOutro))
				.andExpect(jsonPath("$.ligado").value(true)).andExpect(jsonPath("$.gateway").value("SIMULADO"));
		enviar(post("/api/cobrancas/boletos"), sindicoOutro, "{}").andExpect(status().isAccepted())
				.andExpect(jsonPath("$.pedidos").value(1));
		processador.processar();
		mvc.perform(get("/api/cobrancas/{id}", cobranca).header("Authorization", sindicoOutro))
				.andExpect(jsonPath("$.boleto.situacao").value("EMITIDO"));
	}

	@Test
	void avisoDoGatewaySoComOTokenDoCondominio() throws Exception {
		String aviso = """
				{"event": "PAYMENT_RECEIVED", "payment": {"id": "pay_desconhecido"}}""";
		mvc.perform(post("/api/avisos/asaas/{id}", condominio).contentType(MediaType.APPLICATION_JSON).content(aviso))
				.andExpect(status().isUnauthorized());
		mvc.perform(post("/api/avisos/asaas/{id}", condominio).header("asaas-access-token", "token-errado")
						.contentType(MediaType.APPLICATION_JSON).content(aviso))
				.andExpect(status().isUnauthorized());
		// O token de um condomínio não vale para outro.
		UUID outro = cenario.condominio("Outro");
		mvc.perform(post("/api/avisos/asaas/{id}", outro).header("asaas-access-token", tokenDoAviso)
						.contentType(MediaType.APPLICATION_JSON).content(aviso))
				.andExpect(status().isUnauthorized());
		mvc.perform(post("/api/avisos/asaas/{id}", condominio).header("asaas-access-token", tokenDoAviso)
						.contentType(MediaType.APPLICATION_JSON).content(aviso))
				.andExpect(status().isOk());
	}

	@Test
	void outroCondominioEOutroMoradorNaoVeemO2aVia() throws Exception {
		String cobranca = avulsa(unidade, 200, cenario.hoje().plusDays(10));
		processador.processar();

		UUID outro = cenario.condominio("Vizinho");
		String sindicoVizinho = cenario.token(cenario.comPapel(outro, Perfil.SINDICO));
		mvc.perform(get("/api/cobrancas/{id}/pagamento", cobranca).header("Authorization", sindicoVizinho))
				.andExpect(status().isNotFound());
		enviar(post("/api/cobrancas/{id}/pagamento-simulado", cobranca), sindicoVizinho, "{}").andExpect(status().isNotFound());

		Usuario carlos = cenario.usuario("carlos");
		cenario.perfil(condominio, carlos, Perfil.MORADOR);
		mvc.perform(get("/api/eu/cobrancas/{id}/pagamento", cobranca).header("Authorization", cenario.token(carlos)))
				.andExpect(status().isNotFound());
	}

	@Test
	void soOAdministradorLigaOGatewayEAChaveEConferida() throws Exception {
		enviar(put("/api/plataforma/condominios/{id}/gateway", condominio), sindico, """
				{"gateway": "SIMULADO", "ambiente": "SANDBOX", "chave": "qualquer"}""").andExpect(status().isForbidden());
		enviar(put("/api/plataforma/condominios/{id}/gateway", condominio), admin, """
				{"gateway": "SIMULADO", "ambiente": "SANDBOX", "chave": "invalida"}""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("A chave de API não foi aceita pelo gateway"));
		mvc.perform(get("/api/plataforma/condominios/{id}/gateway", condominio).header("Authorization", admin))
				.andExpect(jsonPath("$.situacao.configurado").value(true))
				.andExpect(jsonPath("$.situacao.ativo").value(true))
				.andExpect(jsonPath("$.disponiveis", hasItem("ASAAS")))
				.andExpect(jsonPath("$.disponiveis", hasItem("SIMULADO")));

		mvc.perform(post("/api/plataforma/condominios/{id}/gateway/desativacao", condominio).header("Authorization", admin))
				.andExpect(status().isNoContent());
		String cobranca = avulsa(unidade, 10, cenario.hoje().plusDays(1));
		mvc.perform(get("/api/cobrancas/{id}", cobranca).header("Authorization", sindico))
				.andExpect(jsonPath("$.boleto").value(nullValue()));
	}

	// ----- apoio -----

	private String ligarGateway(UUID id) throws Exception {
		String resposta = enviar(put("/api/plataforma/condominios/{id}/gateway", id), admin, """
				{"gateway": "SIMULADO", "ambiente": "SANDBOX", "chave": "chave-simulada"}""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.url").value("http://localhost:8080/api/avisos/simulado/" + id))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(resposta, "$.token");
	}

	private String avulsa(String unidadeId, int valor, LocalDate vencimento) throws Exception {
		return JsonPath.read(enviar(post("/api/cobrancas"), sindico, """
				{"unidadeId": "%s", "tipo": "MULTA_DISCIPLINAR", "descricao": "Multa por barulho", "valor": %d, "vencimento": "%s"}"""
				.formatted(unidadeId, valor, vencimento)).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.id");
	}

	private void vincular(String pessoa, String unidadeId) throws Exception {
		vincular(pessoa, unidadeId, sindico);
	}

	private void vincular(String pessoa, String unidadeId, String token) throws Exception {
		enviar(post("/api/pessoas/{id}/vinculos", pessoa), token, """
				{"unidadeId": "%s", "tipo": "PROPRIETARIO"}""".formatted(unidadeId)).andExpect(status().isCreated());
	}

	/** O id do boleto no gateway, lido direto do banco (a API não expõe). */
	private String idNoGateway(String cobranca) {
		return TenantAtual.executarComo(condominio, () -> boletos.vigente(UUID.fromString(cobranca)).orElseThrow().getIdNoGateway());
	}

	private ResultActions enviar(MockHttpServletRequestBuilder pedido, String token, String corpo) throws Exception {
		return mvc.perform(pedido.contentType(MediaType.APPLICATION_JSON).content(corpo).header("Authorization", token));
	}

}
