package br.com.condomix.acesso.cadastro;

import br.com.condomix.CenarioDeTeste;
import br.com.condomix.TestcontainersConfiguration;
import br.com.condomix.acesso.Perfil;
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

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cadastro de unidades, pessoas e moradores pelo síndico, "minhas unidades" e isolamento. */
@Import({ TestcontainersConfiguration.class, CenarioDeTeste.class })
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CadastroDoCondominioTests {

	@Autowired MockMvc mvc;
	@Autowired CenarioDeTeste cenario;

	UUID condominio;
	String sindico;

	@BeforeEach
	void prepararCondominio() throws Exception {
		condominio = cenario.condominio("Residencial");
		sindico = cenario.token(cenario.comPapel(condominio, Perfil.SINDICO));
	}

	@Test
	void unidadesIndividuaisELoteComFracoesQueSomam100() throws Exception {
		enviar(post("/api/unidades"), sindico, """
				{"bloco": "A", "identificacao": "101", "tipo": "APARTAMENTO", "fracaoIdeal": 0.5}""")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.descricao").value("A-101"));
		enviar(post("/api/unidades/lote"), sindico, """
				{"bloco": "B", "tipo": "APARTAMENTO", "identificacoes": ["101", "102"]}""")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$", hasSize(2)));

		mvc.perform(get("/api/unidades/resumo").header("Authorization", sindico))
				.andExpect(jsonPath("$.ativas").value(3))
				.andExpect(jsonPath("$.semFracaoIdeal").value(2))
				.andExpect(jsonPath("$.fracoesConferem").value(false))
				.andExpect(jsonPath("$.blocos", hasSize(2)));

		for (String unidade : JsonPath.<java.util.List<String>>read(mvc.perform(get("/api/unidades").param("bloco", "B")
				.header("Authorization", sindico)).andReturn().getResponse().getContentAsString(), "$[*].id")) {
			enviar(put("/api/unidades/{id}", unidade), sindico, """
					{"bloco": "B", "identificacao": "%s", "tipo": "APARTAMENTO", "fracaoIdeal": 0.25}"""
					.formatted(identificacao(unidade)))
					.andExpect(status().isOk());
		}
		mvc.perform(get("/api/unidades/resumo").header("Authorization", sindico))
				.andExpect(jsonPath("$.somaDasFracoes").value(1.0))
				.andExpect(jsonPath("$.fracoesConferem").value(true));
	}

	@Test
	void regrasDasUnidades() throws Exception {
		enviar(post("/api/unidades"), sindico, """
				{"identificacao": "Casa 1", "tipo": "CASA"}""").andExpect(status().isCreated());
		enviar(post("/api/unidades"), sindico, """
				{"identificacao": "Casa 1", "tipo": "CASA"}""").andExpect(status().isConflict());
		enviar(post("/api/unidades"), sindico, """
				{"identificacao": "Casa 2", "tipo": "CASA", "fracaoIdeal": 1.5}""")
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("fração")));
		enviar(post("/api/unidades/lote"), sindico, """
				{"tipo": "CASA", "identificacoes": ["Casa 3", "Casa 1"]}""").andExpect(status().isConflict());
		mvc.perform(get("/api/unidades").header("Authorization", sindico))
				.andExpect(jsonPath("$", hasSize(1))); // o lote com conflito não criou a Casa 3: tudo ou nada
	}

	@Test
	void pessoasComDocumentoValidado() throws Exception {
		enviar(post("/api/pessoas"), sindico, """
				{"nome": "Ana", "documento": "529.982.247-24"}""")
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("CPF inválido"));
		enviar(post("/api/pessoas"), sindico, """
				{"nome": "Ana", "documento": "529.982.247-25", "telefone": "(91) 99999-8888", "email": "ANA@Teste.com"}""")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.documento").value("52998224725"))
				.andExpect(jsonPath("$.telefone").value("5591999998888"))
				.andExpect(jsonPath("$.email").value("ana@teste.com"));
		enviar(post("/api/pessoas"), sindico, """
				{"nome": "Ana de novo", "documento": "52998224725"}""").andExpect(status().isConflict());
		enviar(post("/api/pessoas"), sindico, """
				{"nome": "Empresa Nova", "documento": "12.ABC.345/01DE-35"}""")
				.andExpect(status().isCreated()).andExpect(jsonPath("$.documento").value("12ABC34501DE35"));
		mvc.perform(get("/api/pessoas").param("busca", "empresa").header("Authorization", sindico))
				.andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void moradorCadastradoNumPassoAceitaConviteEVeSuaUnidade() throws Exception {
		String unidade = idDe(enviar(post("/api/unidades"), sindico, """
				{"bloco": "A", "identificacao": "302", "tipo": "APARTAMENTO"}"""));
		String email = "joao-" + UUID.randomUUID() + "@teste.com";
		String resposta = enviar(post("/api/moradores"), sindico, """
				{"pessoa": {"nome": "João", "email": "%s"}, "unidadeId": "%s", "vinculo": "INQUILINO", "inicio": "%s"}"""
				.formatted(email, unidade, cenario.hoje().minusDays(10)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.pessoa.unidades[0].unidade").value("A-302"))
				.andExpect(jsonPath("$.convite.email").value(email))
				.andReturn().getResponse().getContentAsString();
		String codigo = ((String) JsonPath.read(resposta, "$.convite.link")).split("codigo=")[1];

		enviar(post("/api/convites/aceite"), null, """
				{"codigo": "%s", "senha": "senha-do-joao"}""".formatted(codigo)).andExpect(status().isOk());
		String joao = "Bearer " + JsonPath.read(enviar(post("/api/auth/login"), null, """
				{"email": "%s", "senha": "senha-do-joao"}""".formatted(email))
				.andExpect(jsonPath("$.perfis[0]").value("MORADOR"))
				.andReturn().getResponse().getContentAsString(), "$.token");

		mvc.perform(get("/api/eu/unidades").header("Authorization", joao))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].unidade").value("A-302"))
				.andExpect(jsonPath("$[0].vinculo").value("INQUILINO"));

		String pessoa = JsonPath.read(resposta, "$.pessoa.id");
		String vinculo = JsonPath.read(resposta, "$.pessoa.unidades[0].vinculoId");
		enviar(post("/api/unidades/{id}/desativacao", unidade), sindico, "").andExpect(status().isConflict());
		enviar(post("/api/pessoas/{p}/vinculos/{v}/encerramento", pessoa, vinculo), sindico, """
				{"fim": "%s"}""".formatted(cenario.hoje().minusDays(1))).andExpect(status().isOk());
		mvc.perform(get("/api/eu/unidades").header("Authorization", joao)).andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(get("/api/pessoas/{id}", pessoa).header("Authorization", sindico))
				.andExpect(jsonPath("$.unidades[0].fim").value(cenario.hoje().minusDays(1).toString())); // histórico
		enviar(post("/api/unidades/{id}/desativacao", unidade), sindico, "").andExpect(status().isNoContent());
	}

	@Test
	void permissoes() throws Exception {
		String morador = cenario.token(cenario.comPapel(condominio, Perfil.MORADOR));
		String conselheiro = cenario.token(cenario.comPapel(condominio, Perfil.CONSELHEIRO));

		enviar(post("/api/unidades"), morador, """
				{"identificacao": "X", "tipo": "OUTRO"}""").andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail").value("Seu perfil não permite esta ação"));
		mvc.perform(get("/api/pessoas").header("Authorization", morador)).andExpect(status().isForbidden());
		mvc.perform(get("/api/unidades").header("Authorization", conselheiro)).andExpect(status().isOk());
		enviar(post("/api/pessoas"), conselheiro, """
				{"nome": "Y"}""").andExpect(status().isForbidden());
	}

	@Test
	void outroCondominioNaoVeNemMexe() throws Exception {
		String unidadeDeA = idDe(enviar(post("/api/unidades"), sindico, """
				{"identificacao": "Sala 1", "tipo": "SALA"}"""));
		String pessoaDeA = idDe(enviar(post("/api/pessoas"), sindico, """
				{"nome": "Pessoa de A"}"""));
		UUID b = cenario.condominio("B");
		String sindicoB = cenario.token(cenario.comPapel(b, Perfil.SINDICO));
		String pessoaDeB = idDe(enviar(post("/api/pessoas"), sindicoB, """
				{"nome": "Pessoa de B"}"""));

		mvc.perform(get("/api/unidades").header("Authorization", sindicoB)).andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(get("/api/unidades/{id}", unidadeDeA).header("Authorization", sindicoB))
				.andExpect(status().isNotFound());
		mvc.perform(get("/api/pessoas/{id}", pessoaDeA).header("Authorization", sindicoB))
				.andExpect(status().isNotFound());
		enviar(put("/api/unidades/{id}", unidadeDeA), sindicoB, """
				{"identificacao": "Invadida", "tipo": "SALA"}""").andExpect(status().isNotFound());
		enviar(post("/api/pessoas/{id}/vinculos", pessoaDeB), sindicoB, """
				{"unidadeId": "%s", "tipo": "MORADOR"}""".formatted(unidadeDeA)).andExpect(status().isNotFound());
	}

	// ----- apoio -----

	private ResultActions enviar(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder pedido,
			String token, String corpo) throws Exception {
		pedido.contentType(MediaType.APPLICATION_JSON).content(corpo);
		if (token != null) {
			pedido.header("Authorization", token);
		}
		return mvc.perform(pedido);
	}

	private static String idDe(ResultActions resposta) throws Exception {
		return JsonPath.read(resposta.andReturn().getResponse().getContentAsString(), "$.id");
	}

	private String identificacao(String unidadeId) throws Exception {
		return JsonPath.read(mvc.perform(get("/api/unidades/{id}", unidadeId).header("Authorization", sindico))
				.andReturn().getResponse().getContentAsString(), "$.identificacao");
	}

}
