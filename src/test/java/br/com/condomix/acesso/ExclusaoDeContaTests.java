package br.com.condomix.acesso;

import br.com.condomix.CenarioDeTeste;
import br.com.condomix.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Excluir a própria conta (exigência da App Store): o login some, o cadastro e as cobranças ficam com o condomínio.
 * E o CORS do app instalado.
 */
@Import({ TestcontainersConfiguration.class, CenarioDeTeste.class })
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExclusaoDeContaTests {

	@Autowired MockMvc mvc;
	@Autowired CenarioDeTeste cenario;

	UUID condominio;
	String sindico;
	Usuario ana;
	String pessoaAna;
	String unidade;

	@BeforeEach
	void prepararMoradoraComCobranca() throws Exception {
		condominio = cenario.condominio("Residencial da Exclusão " + UUID.randomUUID());
		sindico = cenario.token(cenario.comPapel(condominio, Perfil.SINDICO));
		enviar(put("/api/financeiro/configuracao"), sindico, """
				{"tipoRateio": "IGUALITARIO", "diaVencimento": 10, "multaPercentual": 2, "jurosMensalPercentual": 1,
				 "fundoReservaPercentual": 0}""").andExpect(status().isOk());
		unidade = JsonPath.read(enviar(post("/api/unidades"), sindico, """
				{"identificacao": "101", "tipo": "APARTAMENTO"}""").andReturn().getResponse().getContentAsString(), "$.id");
		ana = cenario.usuario("ana");
		cenario.perfil(condominio, ana, Perfil.MORADOR);
		pessoaAna = cenario.pessoa(condominio, "Ana Proprietária", ana).getId().toString();
		enviar(post("/api/pessoas/{id}/vinculos", pessoaAna), sindico, """
				{"unidadeId": "%s", "tipo": "PROPRIETARIO"}""".formatted(unidade)).andExpect(status().isCreated());
		enviar(post("/api/cobrancas"), sindico, """
				{"unidadeId": "%s", "tipo": "MULTA_DISCIPLINAR", "descricao": "Multa", "valor": 50, "vencimento": "%s"}"""
				.formatted(unidade, cenario.hoje().plusDays(10))).andExpect(status().isCreated());
	}

	@Test
	void excluirAContaApagaOLoginEMantemOCadastroEAsCobrancasNoCondominio() throws Exception {
		String token = cenario.token(ana);
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
				mvc.perform(get("/api/notificacoes").header("Authorization", token)).andExpect(jsonPath("$", hasSize(1))));

		enviar(post("/api/auth/exclusao-da-conta"), token, """
				{"senha": "errada"}""").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("A senha não confere."));
		enviar(post("/api/auth/exclusao-da-conta"), token, """
				{"senha": "%s"}""".formatted(CenarioDeTeste.SENHA)).andExpect(status().isNoContent());

		// O token que ela tinha não vale mais, e o login não entra.
		mvc.perform(get("/api/eu").header("Authorization", token)).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "senha": "%s"}""".formatted(ana.getEmail(), CenarioDeTeste.SENHA)))
				.andExpect(status().isUnauthorized());
		// O cadastro e a cobrança ficam com o condomínio, sem acesso ao app.
		mvc.perform(get("/api/pessoas/{id}", pessoaAna).header("Authorization", sindico))
				.andExpect(jsonPath("$.nome").value("Ana Proprietária"))
				.andExpect(jsonPath("$.temLogin").value(false));
		mvc.perform(get("/api/cobrancas").header("Authorization", sindico))
				.andExpect(jsonPath("$.itens[?(@.unidade == '101')]", hasSize(1)));
		// As notificações dela foram apagadas (o síndico confere pelo próprio sino: nada dela vaza para ele).
		mvc.perform(get("/api/notificacoes").header("Authorization", sindico)).andExpect(jsonPath("$", empty()));
	}

	@Test
	void administradorDaPlataformaNaoExcluiPeloApp() throws Exception {
		String admin = cenario.token(cenario.administrador());
		enviar(post("/api/auth/exclusao-da-conta"), admin, """
				{"senha": "%s"}""".formatted(CenarioDeTeste.SENHA)).andExpect(status().isConflict());
	}

	@Test
	void appInstaladoPodeChamarAApi() throws Exception {
		for (String origem : new String[] { "capacitor://localhost", "https://localhost" }) {
			mvc.perform(options("/api/auth/login").header(HttpHeaders.ORIGIN, origem)
							.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
							.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
					.andExpect(status().isOk())
					.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origem));
		}
		mvc.perform(options("/api/auth/login").header(HttpHeaders.ORIGIN, "https://site-qualquer.com")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(status().isForbidden());
	}

	private ResultActions enviar(MockHttpServletRequestBuilder pedido, String token, String corpo) throws Exception {
		return mvc.perform(pedido.contentType(MediaType.APPLICATION_JSON).content(corpo).header("Authorization", token));
	}

}
