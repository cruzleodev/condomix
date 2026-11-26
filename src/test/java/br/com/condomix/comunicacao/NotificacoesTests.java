package br.com.condomix.comunicacao;

import br.com.condomix.CenarioDeTeste;
import br.com.condomix.TestcontainersConfiguration;
import br.com.condomix.acesso.Perfil;
import br.com.condomix.acesso.Usuario;
import br.com.condomix.compartilhado.EnvioDeEmail;
import br.com.condomix.comunicacao.aplicacao.EntregaDeNotificacoes;
import br.com.condomix.comunicacao.push.AparelhoDeTeste;
import br.com.condomix.faturamento.aplicacao.LembretesDaAssinatura;
import br.com.condomix.financeiro.aplicacao.LembretesDeCobranca;
import br.com.condomix.reservas.aplicacao.LembretesDeReserva;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Notificações de ponta a ponta: o acontecimento (cobrança, reserva, documento, fatura) vira notificação no sino de
 * quem deve receber, com e-mail e push conforme as preferências, sem repetir e sem vazar para outro condomínio.
 * Os ouvintes rodam em segundo plano: os testes esperam o resultado (Awaitility).
 */
@Import({ TestcontainersConfiguration.class, CenarioDeTeste.class, NotificacoesTests.CaixaDeSaida.class })
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificacoesTests {

	/** Um servidor de e-mail de mentira: guarda o que foi enviado. */
	@TestConfiguration
	static class CaixaDeSaida {

		static final List<EnvioDeEmail.MensagemDeEmail> ENVIADOS = new CopyOnWriteArrayList<>();

		@Bean
		@Primary
		EnvioDeEmail emailDeTeste() {
			return new EnvioDeEmail() {
				@Override
				public boolean disponivel() {
					return true;
				}

				@Override
				public void enviar(MensagemDeEmail mensagem) {
					ENVIADOS.add(mensagem);
				}
			};
		}

	}

	@Autowired MockMvc mvc;
	@Autowired CenarioDeTeste cenario;
	@Autowired EntregaDeNotificacoes entrega;
	@Autowired LembretesDeCobranca lembretesDeCobranca;
	@Autowired LembretesDeReserva lembretesDeReserva;
	@Autowired LembretesDaAssinatura lembretesDaAssinatura;

	UUID condominio;
	Usuario sindicoLogin;
	String sindico;
	String unidade;
	Usuario ana;
	String tokenAna;

	@BeforeEach
	void prepararCondominioComUmaProprietaria() throws Exception {
		condominio = cenario.condominio("Residencial dos Avisos " + UUID.randomUUID());
		sindicoLogin = cenario.comPapel(condominio, Perfil.SINDICO);
		sindico = cenario.token(sindicoLogin);
		enviar(put("/api/financeiro/configuracao"), sindico, """
				{"tipoRateio": "IGUALITARIO", "diaVencimento": 10, "multaPercentual": 2, "jurosMensalPercentual": 1,
				 "fundoReservaPercentual": 0}""").andExpect(status().isOk());
		unidade = JsonPath.read(enviar(post("/api/unidades"), sindico, """
				{"identificacao": "101", "tipo": "APARTAMENTO"}""").andReturn().getResponse().getContentAsString(), "$.id");
		ana = cenario.usuario("ana");
		cenario.perfil(condominio, ana, Perfil.MORADOR);
		String pessoa = cenario.pessoa(condominio, "Ana Proprietária", ana).getId().toString();
		enviar(post("/api/pessoas/{id}/vinculos", pessoa), sindico, """
				{"unidadeId": "%s", "tipo": "PROPRIETARIO"}""".formatted(unidade)).andExpect(status().isCreated());
		tokenAna = cenario.token(ana);
	}

	@Test
	void cobrancaNovaVaiParaOSinoEPorEmailDeQuemAcompanhaOsDebitosEDepoisOPagamento() throws Exception {
		// Além da proprietária: um morador (também é avisado) e um dependente (não é).
		String tokenBruno = cenario.token(vinculado("bruno", "Bruno Morador", "MORADOR"));
		String tokenDani = cenario.token(vinculado("dani", "Dani Dependente", "DEPENDENTE"));
		String cobranca = avulsa(cenario.hoje().plusDays(10));

		notificacoes(tokenAna, 1);
		notificacoes(tokenBruno, 1);
		mvc.perform(get("/api/notificacoes").header("Authorization", tokenDani)).andExpect(jsonPath("$", empty()));
		mvc.perform(get("/api/notificacoes").header("Authorization", tokenAna))
				.andExpect(jsonPath("$[0].titulo").value("Nova cobrança"))
				.andExpect(jsonPath("$[0].texto").value(containsString("Multa por barulho: R$ 120,00, vence em ")))
				.andExpect(jsonPath("$[0].link").value("/debitos"))
				.andExpect(jsonPath("$[0].lida").value(false));
		mvc.perform(get("/api/notificacoes/nao-lidas").header("Authorization", tokenAna)).andExpect(jsonPath("$.quantidade").value(1));
		// O síndico (não é da unidade) não recebe.
		mvc.perform(get("/api/notificacoes").header("Authorization", sindico)).andExpect(jsonPath("$", empty()));

		entrega.entregarPendentes();
		var email = CaixaDeSaida.ENVIADOS.stream().filter(m -> m.para().equals(ana.getEmail())).findFirst().orElseThrow();
		assertThat(email.assunto()).isEqualTo("Nova cobrança");
		assertThat(email.nomeDoDestinatario()).isEqualTo("Ana Proprietária");
		assertThat(email.linkDoBotao()).isEqualTo("http://localhost:5173/debitos");

		enviar(post("/api/cobrancas/{id}/pagamento", cobranca), sindico, """
				{"forma": "PIX"}""").andExpect(status().isOk());
		notificacoes(tokenAna, 2);
		mvc.perform(get("/api/notificacoes").header("Authorization", tokenAna))
				.andExpect(jsonPath("$[0].titulo").value("Pagamento confirmado"));

		enviar(post("/api/notificacoes/leitura"), tokenAna, "{}").andExpect(status().isNoContent());
		mvc.perform(get("/api/notificacoes/nao-lidas").header("Authorization", tokenAna)).andExpect(jsonPath("$.quantidade").value(0));
	}

	@Test
	void lembreteDoDiaNaoSeRepeteEPreferenciaDesligaOEmail() throws Exception {
		enviar(put("/api/notificacoes/preferencias"), tokenAna, """
				[{"grupo": "COBRANCAS", "email": false, "push": true}]""")
				.andExpect(jsonPath("$.grupos[?(@.grupo == 'COBRANCAS')].email").value(false))
				.andExpect(jsonPath("$.emailLigado").value(true));
		avulsa(cenario.hoje().plusDays(3));
		notificacoes(tokenAna, 1);

		lembretesDeCobranca.lembrar();
		lembretesDeCobranca.lembrar(); // de novo no mesmo dia: não repete
		notificacoes(tokenAna, 2);
		Thread.sleep(300);
		mvc.perform(get("/api/notificacoes").header("Authorization", tokenAna))
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].titulo").value("Vencimento chegando"));

		int antes = CaixaDeSaida.ENVIADOS.size();
		entrega.entregarPendentes();
		assertThat(CaixaDeSaida.ENVIADOS.subList(antes, CaixaDeSaida.ENVIADOS.size()))
				.noneMatch(m -> m.para().equals(ana.getEmail()));
	}

	@Test
	void reservaEOLembreteDaVesperaChegamAQuemUsaAUnidadeTambemPorPush() throws Exception {
		var aparelho = new AparelhoDeTeste();
		var recebidas = new CopyOnWriteArrayList<String>();
		HttpServer servicoDePush = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		servicoDePush.createContext("/push", troca -> {
			try {
				recebidas.add(aparelho.decifrar(troca.getRequestBody().readAllBytes()));
			}
			catch (Exception e) {
				recebidas.add("erro");
			}
			troca.sendResponseHeaders(201, -1);
			troca.close();
		});
		servicoDePush.start();
		try {
			mvc.perform(get("/api/notificacoes/push/chave").header("Authorization", tokenAna))
					.andExpect(jsonPath("$.chavePublica").value(containsString("")));
			enviar(post("/api/notificacoes/push/inscricao"), tokenAna, """
					{"endpoint": "http://localhost:%d/push/ana", "p256dh": "%s", "auth": "%s"}"""
					.formatted(servicoDePush.getAddress().getPort(), aparelho.p256dh(), aparelho.auth())).andExpect(status().isNoContent());
			mvc.perform(get("/api/notificacoes/preferencias").header("Authorization", tokenAna))
					.andExpect(jsonPath("$.aparelhoComPush").value(true));

			String salao = JsonPath.read(enviar(post("/api/espacos"), sindico, """
					{"nome": "Salão", "horarios": [{"nome": "Noite", "inicio": "18:00", "fim": "23:00"}]}""")
					.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
			enviar(post("/api/reservas"), sindico, """
					{"espacoId": "%s", "data": "%s", "horario": "Noite", "unidadeId": "%s"}"""
					.formatted(salao, cenario.hoje().plusDays(1), unidade)).andExpect(status().isCreated());

			notificacoes(tokenAna, 1);
			mvc.perform(get("/api/notificacoes").header("Authorization", tokenAna))
					.andExpect(jsonPath("$[0].titulo").value("Reserva confirmada"))
					.andExpect(jsonPath("$[0].texto").value(containsString("Reserva feita pela administração do condomínio.")));
			entrega.entregarPendentes();
			assertThat(recebidas).anyMatch(r -> r.contains("\"titulo\":\"Reserva confirmada\"") && r.contains("\"link\":\"/reservas\""));

			lembretesDeReserva.lembrar();
			notificacoes(tokenAna, 2);
			mvc.perform(get("/api/notificacoes").header("Authorization", tokenAna))
					.andExpect(jsonPath("$[0].titulo").value("Sua reserva é amanhã"));
		}
		finally {
			servicoDePush.stop(0);
		}
	}

	@Test
	void documentoNovoAvisaTodosEOTesteTerminandoAvisaSoAGestao() throws Exception {
		mvc.perform(multipart("/api/documentos")
						.file(new MockMultipartFile("arquivo", "regimento.pdf", "application/pdf", "%PDF-1.4 conteudo".getBytes()))
						.param("titulo", "Regimento interno").param("categoria", "REGIMENTO_INTERNO")
						.header("Authorization", sindico))
				.andExpect(status().isCreated());
		notificacoes(tokenAna, 1);
		notificacoes(sindico, 1);
		mvc.perform(get("/api/notificacoes").header("Authorization", tokenAna))
				.andExpect(jsonPath("$[0].titulo").value("Documento novo: Regimento interno"));

		String admin = cenario.token(cenario.administrador());
		enviar(put("/api/plataforma/condominios/{id}/fim-do-teste", condominio), admin, """
				{"fimDoTeste": "%s"}""".formatted(cenario.hoje().plusDays(7))).andExpect(status().isOk());
		lembretesDaAssinatura.lembrar();
		notificacoes(sindico, 2);
		mvc.perform(get("/api/notificacoes").header("Authorization", sindico))
				.andExpect(jsonPath("$[0].titulo").value("Seu teste grátis termina em breve"))
				.andExpect(jsonPath("$[0].link").value("/gestao/assinatura"));
		Thread.sleep(300);
		mvc.perform(get("/api/notificacoes").header("Authorization", tokenAna)).andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void conviteSaiPorEmailComOLink() throws Exception {
		String pessoa = cenario.pessoa(condominio, "Beto Inquilino", null).getId().toString();
		String resposta = enviar(post("/api/convites"), sindico, """
				{"pessoaId": "%s", "email": "beto-%s@teste.com"}""".formatted(pessoa, UUID.randomUUID()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.enviadoPorEmail").value(true))
				.andReturn().getResponse().getContentAsString();
		String link = JsonPath.read(resposta, "$.link");
		var email = CaixaDeSaida.ENVIADOS.stream().filter(m -> link.equals(m.linkDoBotao())).findFirst().orElseThrow();
		assertThat(email.assunto()).startsWith("Seu acesso ao Condomix: Residencial dos Avisos");
		assertThat(email.nomeDoDestinatario()).isEqualTo("Beto Inquilino");
	}

	@Test
	void outroCondominioNaoVeAsNotificacoes() throws Exception {
		avulsa(cenario.hoje().plusDays(10));
		notificacoes(tokenAna, 1);
		String id = JsonPath.read(mvc.perform(get("/api/notificacoes").header("Authorization", tokenAna))
				.andReturn().getResponse().getContentAsString(), "$[0].id");
		UUID vizinho = cenario.condominio("Condomínio Vizinho " + UUID.randomUUID());
		String sindicoVizinho = cenario.token(cenario.comPapel(vizinho, Perfil.SINDICO));
		mvc.perform(get("/api/notificacoes").header("Authorization", sindicoVizinho)).andExpect(jsonPath("$", empty()));
		enviar(post("/api/notificacoes/{id}/leitura", id), sindicoVizinho, "{}").andExpect(status().isNotFound());
		enviar(post("/api/notificacoes/{id}/leitura", id), sindico, "{}").andExpect(status().isNotFound());
	}

	// ----- ajudantes -----

	private String avulsa(java.time.LocalDate vencimento) throws Exception {
		return JsonPath.read(enviar(post("/api/cobrancas"), sindico, """
				{"unidadeId": "%s", "tipo": "MULTA_DISCIPLINAR", "descricao": "Multa por barulho", "valor": 120, "vencimento": "%s"}"""
				.formatted(unidade, vencimento)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	/** Espera as notificações chegarem (os ouvintes rodam em segundo plano). */
	private Usuario vinculado(String apelido, String nome, String tipo) throws Exception {
		Usuario usuario = cenario.usuario(apelido);
		cenario.perfil(condominio, usuario, Perfil.MORADOR);
		String pessoa = cenario.pessoa(condominio, nome, usuario).getId().toString();
		enviar(post("/api/pessoas/{id}/vinculos", pessoa), sindico, """
				{"unidadeId": "%s", "tipo": "%s"}""".formatted(unidade, tipo)).andExpect(status().isCreated());
		return usuario;
	}

	private void notificacoes(String token, int quantas) {
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
				mvc.perform(get("/api/notificacoes").header("Authorization", token)).andExpect(jsonPath("$", hasSize(quantas))));
	}

	private ResultActions enviar(MockHttpServletRequestBuilder pedido, String token, String corpo) throws Exception {
		return mvc.perform(pedido.contentType(MediaType.APPLICATION_JSON).content(corpo).header("Authorization", token));
	}

}
