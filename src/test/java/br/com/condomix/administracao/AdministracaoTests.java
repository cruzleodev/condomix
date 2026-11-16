package br.com.condomix.administracao;

import br.com.condomix.CenarioDeTeste;
import br.com.condomix.TestcontainersConfiguration;
import br.com.condomix.acesso.Perfil;
import br.com.condomix.acesso.Usuario;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Administração da plataforma: cliente novo, convite, planos, assinatura, suspensão e modo suporte. */
@Import({ TestcontainersConfiguration.class, CenarioDeTeste.class })
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdministracaoTests {

	@Autowired MockMvc mvc;
	@Autowired CenarioDeTeste cenario;

	String admin;

	@BeforeEach
	void entrarComoAdministrador() throws Exception {
		admin = cenario.token(cenario.administrador());
	}

	@Test
	void administradorRecebeOPapelESoEleEntraNaPlataforma() throws Exception {
		mvc.perform(get("/api/eu").header("Authorization", admin))
				.andExpect(jsonPath("$.perfis", containsInAnyOrder("ADMIN_PLATAFORMA")));
		UUID condominio = cenario.condominio("Residencial");
		String sindico = cenario.token(cenario.comPapel(condominio, Perfil.SINDICO));
		mvc.perform(get("/api/plataforma/condominios").header("Authorization", sindico)).andExpect(status().isForbidden());
		mvc.perform(get("/api/plataforma/condominios").header("Authorization", admin)).andExpect(status().isOk());
	}

	@Test
	void clienteNovoComSindicoQueAceitaOConviteEEntra() throws Exception {
		String email = "sindica-" + UUID.randomUUID() + "@teste.com";
		String resposta = clienteNovo("Residencial Primavera", email).andExpect(status().isCreated())
				.andExpect(jsonPath("$.condominio.cnpj").value("12ABC34501DE35"))
				.andExpect(jsonPath("$.condominio.assinatura.plano").value("COMPLETO"))
				.andExpect(jsonPath("$.condominio.assinatura.status").value("TESTE"))
				.andExpect(jsonPath("$.conviteDoSindico.email").value(email))
				.andExpect(jsonPath("$.conviteDoSindico.link").value(startsWith("http://localhost:5173/convite?codigo=")))
				.andReturn().getResponse().getContentAsString();
		String codigo = ((String) JsonPath.read(resposta, "$.conviteDoSindico.link")).split("codigo=")[1];

		postJson("/api/convites/consulta", null, """
				{"codigo": "%s"}""".formatted(codigo))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.condominio").value("Residencial Primavera"))
				.andExpect(jsonPath("$.jaTemLogin").value(false));
		postJson("/api/convites/aceite", null, """
				{"codigo": "%s", "senha": "curta"}""".formatted(codigo)).andExpect(status().isBadRequest());
		postJson("/api/convites/aceite", null, """
				{"codigo": "%s", "senha": "senha-da-sindica"}""".formatted(codigo)).andExpect(status().isOk());
		postJson("/api/convites/aceite", null, """
				{"codigo": "%s", "senha": "senha-da-sindica"}""".formatted(codigo))
				.andExpect(status().isNotFound()); // convite só vale uma vez

		postJson("/api/auth/login", null, """
				{"email": "%s", "senha": "senha-da-sindica"}""".formatted(email))
				.andExpect(jsonPath("$.condominios[0].nome").value("Residencial Primavera"))
				.andExpect(jsonPath("$.perfis", containsInAnyOrder("SINDICO")));
	}

	@Test
	void clienteNovoComMandatoInvalidoNaoCriaNada() throws Exception {
		String nome = "Condomínio Inválido " + UUID.randomUUID();
		postJson("/api/plataforma/condominios", admin, """
				{"nome": "%s", "tipo": "COMERCIAL", "plano": "ESSENCIAL", "periodicidade": "ANUAL",
				 "sindico": {"nome": "X", "email": "x@teste.com", "inicioMandato": "2026-01-01", "fimMandato": "2029-01-01"}}"""
				.formatted(nome))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/api/plataforma/condominios").header("Authorization", admin))
				.andExpect(jsonPath("$[?(@.nome == '%s')]".formatted(nome), hasSize(0)));
	}

	@Test
	void sindicoConvidaMoradorQueJaTemLoginEmOutroCondominio() throws Exception {
		UUID a = cenario.condominio("A");
		UUID b = cenario.condominio("B");
		Usuario moradorDeA = cenario.comPapel(a, Perfil.MORADOR);
		String sindicoB = cenario.token(cenario.comPapel(b, Perfil.SINDICO));
		UUID pessoaEmB = cenario.pessoa(b, "Morador também de B", null).getId();

		String link = JsonPath.read(postJson("/api/convites", sindicoB, """
				{"pessoaId": "%s", "email": "%s", "perfil": "MORADOR"}""".formatted(pessoaEmB, moradorDeA.getEmail()))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.link");
		String codigo = link.split("codigo=")[1];

		postJson("/api/convites/aceite", null, """
				{"codigo": "%s", "senha": "senha-errada"}""".formatted(codigo)).andExpect(status().isUnauthorized());
		postJson("/api/convites/aceite", null, """
				{"codigo": "%s", "senha": "%s"}""".formatted(codigo, CenarioDeTeste.SENHA)).andExpect(status().isOk());

		postJson("/api/auth/login", null, """
				{"email": "%s", "senha": "%s"}""".formatted(moradorDeA.getEmail(), CenarioDeTeste.SENHA))
				.andExpect(jsonPath("$.condominios", hasSize(2)));
	}

	@Test
	void precosDosPlanos() throws Exception {
		mvc.perform(put("/api/plataforma/planos/{codigo}/precos", "ESSENCIAL").header("Authorization", admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"mensal": 199.90, "semestral": 1079.46, "anual": 1918.80}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.precoMensal").value(199.90));
		mvc.perform(get("/api/plataforma/planos").header("Authorization", admin))
				.andExpect(jsonPath("$[?(@.codigo == 'ESSENCIAL')].precoAnual", hasItem(1918.80)));
		mvc.perform(put("/api/plataforma/planos/{codigo}/precos", "ESSENCIAL").header("Authorization", admin)
						.contentType(MediaType.APPLICATION_JSON).content("""
								{"mensal": -1}"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void trocaDePlanoESuspensao() throws Exception {
		UUID condominio = cenario.condominio("Residencial");
		cenario.token(cenario.comPapel(condominio, Perfil.SINDICO));
		Usuario morador = cenario.comPapel(condominio, Perfil.MORADOR);

		mvc.perform(put("/api/plataforma/condominios/{id}/assinatura", condominio).header("Authorization", admin)
						.contentType(MediaType.APPLICATION_JSON).content("""
								{"plano": "ESSENCIAL", "periodicidade": "ANUAL", "status": "ATIVA"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.assinatura.plano").value("ESSENCIAL"))
				.andExpect(jsonPath("$.assinatura.status").value("ATIVA"));

		mvc.perform(post("/api/plataforma/condominios/{id}/suspensao", condominio).header("Authorization", admin))
				.andExpect(status().isNoContent());
		postJson("/api/auth/login", null, """
				{"email": "%s", "senha": "%s"}""".formatted(morador.getEmail(), CenarioDeTeste.SENHA))
				.andExpect(jsonPath("$.condominios", hasSize(0)));

		mvc.perform(post("/api/plataforma/condominios/{id}/reativacao", condominio).header("Authorization", admin))
				.andExpect(status().isNoContent());
		postJson("/api/auth/login", null, """
				{"email": "%s", "senha": "%s"}""".formatted(morador.getEmail(), CenarioDeTeste.SENHA))
				.andExpect(jsonPath("$.condominios", hasSize(1)));
	}

	@Test
	void modoSuporteSoLeEFicaRegistrado() throws Exception {
		UUID condominio = cenario.condominio("Residencial");
		String sindico = cenario.token(cenario.comPapel(condominio, Perfil.SINDICO));
		mvc.perform(multipart("/api/documentos")
						.file(new MockMultipartFile("arquivo", "c.pdf", "application/pdf", "%PDF-1.7".getBytes()))
						.param("titulo", "Convenção").param("categoria", "CONVENCAO").header("Authorization", sindico))
				.andExpect(status().isCreated());

		postJson("/api/plataforma/suporte", admin, """
				{"condominioId": "%s", "motivo": "curto"}""".formatted(condominio)).andExpect(status().isBadRequest());
		String suporte = "Bearer " + JsonPath.read(postJson("/api/plataforma/suporte", admin, """
				{"condominioId": "%s", "motivo": "Síndico relatou erro ao abrir a convenção"}""".formatted(condominio))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.token");

		mvc.perform(get("/api/documentos").header("Authorization", suporte))
				.andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
		mvc.perform(multipart("/api/documentos")
						.file(new MockMultipartFile("arquivo", "x.pdf", "application/pdf", "%PDF-1.7".getBytes()))
						.param("titulo", "Tentativa").param("categoria", "OUTRO").header("Authorization", suporte))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail").value("O acesso de suporte é somente leitura"));
		mvc.perform(get("/api/plataforma/suporte").header("Authorization", admin))
				.andExpect(jsonPath("$[0].motivo").value(containsString("convenção")))
				.andExpect(jsonPath("$[0].condominioId").value(condominio.toString()));
		postJson("/api/plataforma/suporte", sindico, """
				{"condominioId": "%s", "motivo": "Quero ver outro condomínio"}""".formatted(condominio))
				.andExpect(status().isForbidden());
	}

	// ----- apoio -----

	private ResultActions clienteNovo(String nome, String emailDoSindico) throws Exception {
		return postJson("/api/plataforma/condominios", admin, """
				{"nome": "%s", "tipo": "RESIDENCIAL_VERTICAL", "cnpj": "12.ABC.345/01DE-35", "plano": "COMPLETO",
				 "periodicidade": "MENSAL",
				 "sindico": {"nome": "Sílvia Síndica", "email": "%s", "inicioMandato": "%s", "fimMandato": "%s"}}"""
				.formatted(nome, emailDoSindico, cenario.hoje(), cenario.hoje().plusYears(1).minusDays(1)));
	}

	private ResultActions postJson(String url, String token, String corpo) throws Exception {
		var pedido = post(url).contentType(MediaType.APPLICATION_JSON).content(corpo);
		if (token != null) {
			pedido.header("Authorization", token);
		}
		return mvc.perform(pedido);
	}

}
