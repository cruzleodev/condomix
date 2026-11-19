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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O administrador cuida do síndico, bloqueia logins, encerra o suporte e vê o histórico da assinatura. */
@Import({ TestcontainersConfiguration.class, CenarioDeTeste.class })
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SuporteEAcessosTests {

	@Autowired MockMvc mvc;
	@Autowired CenarioDeTeste cenario;

	Usuario administrador;

	String admin;

	@BeforeEach
	void entrarComoAdministrador() throws Exception {
		administrador = cenario.administrador();
		admin = cenario.token(administrador);
	}

	@Test
	void novoConviteParaOSindicoQuePerdeuOLink() throws Exception {
		String email = "sindico-" + UUID.randomUUID() + "@teste.com";
		String cliente = clienteNovo(email).andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(cliente, "$.condominio.id");
		String codigoAntigo = codigo(JsonPath.read(cliente, "$.conviteDoSindico.link"));

		mvc.perform(get("/api/plataforma/condominios/{id}/sindico", id).header("Authorization", admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.temLogin").value(false))
				.andExpect(jsonPath("$.convitePendenteAte").value(notNullValue()));

		String corrigido = "corrigido-" + email;
		String novo = codigo(JsonPath.read(postJson("/api/plataforma/condominios/" + id + "/sindico/convite", admin, """
				{"email": "%s"}""".formatted(corrigido))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value(corrigido))
				.andReturn().getResponse().getContentAsString(), "$.link"));

		// O link antigo deixa de valer; o novo cria o login.
		postJson("/api/convites/aceite", null, """
				{"codigo": "%s", "senha": "senha-do-sindico"}""".formatted(codigoAntigo))
				.andExpect(status().is4xxClientError());
		postJson("/api/convites/aceite", null, """
				{"codigo": "%s", "senha": "senha-do-sindico"}""".formatted(novo)).andExpect(status().isOk());

		mvc.perform(get("/api/plataforma/condominios/{id}/sindico", id).header("Authorization", admin))
				.andExpect(jsonPath("$.temLogin").value(true))
				.andExpect(jsonPath("$.convitePendenteAte").value(nullValue()));
		postJson("/api/plataforma/condominios/" + id + "/sindico/convite", admin, "{}")
				.andExpect(status().isConflict());
	}

	@Test
	void trocaDoSindicoEncerraOMandatoAnterior() throws Exception {
		String id = JsonPath.read(clienteNovo("antigo-" + UUID.randomUUID() + "@teste.com")
				.andReturn().getResponse().getContentAsString(), "$.condominio.id");
		String email = "nova-" + UUID.randomUUID() + "@teste.com";

		mvc.perform(put("/api/plataforma/condominios/{id}/sindico", id).header("Authorization", admin)
						.contentType(MediaType.APPLICATION_JSON).content("""
								{"nome": "Nova Síndica", "email": "%s", "inicioMandato": "%s", "fimMandato": "%s"}"""
								.formatted(email, cenario.hoje(), cenario.hoje().plusYears(3))))
				.andExpect(status().isBadRequest()); // mais de 2 anos

		mvc.perform(put("/api/plataforma/condominios/{id}/sindico", id).header("Authorization", admin)
						.contentType(MediaType.APPLICATION_JSON).content("""
								{"nome": "Nova Síndica", "email": "%s", "inicioMandato": "%s", "fimMandato": "%s"}"""
								.formatted(email, cenario.hoje(), cenario.hoje().plusYears(1).minusDays(1))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sindico.nome").value("Nova Síndica"))
				.andExpect(jsonPath("$.convite.email").value(email));

		mvc.perform(get("/api/plataforma/condominios/{id}/sindico", id).header("Authorization", admin))
				.andExpect(jsonPath("$.nome").value("Nova Síndica"));
	}

	@Test
	void loginBloqueadoPerdeOAcessoNaHora() throws Exception {
		UUID condominio = cenario.condominio("Residencial");
		Usuario morador = cenario.comPapel(condominio, Perfil.MORADOR);
		String token = cenario.token(morador);
		mvc.perform(get("/api/eu").header("Authorization", token)).andExpect(status().isOk());

		mvc.perform(get("/api/plataforma/logins").param("email", "ab").header("Authorization", admin))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/api/plataforma/logins").param("email", morador.getEmail().toUpperCase()).header("Authorization", admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].bloqueado").value(false))
				.andExpect(jsonPath("$[0].condominios[0].id").doesNotExist())
				.andExpect(jsonPath("$[0].condominios[0].condominioId").value(condominio.toString()))
				.andExpect(jsonPath("$[0].condominios[0].perfis", contains("MORADOR")));

		postJson("/api/plataforma/logins/" + morador.getId() + "/bloqueio", admin, """
				{"motivo": "curto"}""").andExpect(status().isBadRequest());
		postJson("/api/plataforma/logins/" + administrador.getId() + "/bloqueio", admin, """
				{"motivo": "Testando bloquear a mim mesmo"}""").andExpect(status().isBadRequest());
		postJson("/api/plataforma/logins/" + morador.getId() + "/bloqueio", admin, """
				{"motivo": "Suspeita de invasão da conta"}""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bloqueado").value(true))
				.andExpect(jsonPath("$.motivoDoBloqueio").value("Suspeita de invasão da conta"));

		// O token que ele já tinha não vale mais, e o login avisa do bloqueio (só para quem acerta a senha).
		mvc.perform(get("/api/eu").header("Authorization", token)).andExpect(status().isUnauthorized());
		login(morador.getEmail(), CenarioDeTeste.SENHA)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail").value("Seu acesso está bloqueado. Fale com o suporte do Condomix."));
		login(morador.getEmail(), "senha-errada").andExpect(status().isUnauthorized());

		postJson("/api/plataforma/logins/" + morador.getId() + "/desbloqueio", admin, "{}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.bloqueado").value(false));
		login(morador.getEmail(), CenarioDeTeste.SENHA).andExpect(status().isOk());

		String sindico = cenario.token(cenario.comPapel(condominio, Perfil.SINDICO));
		mvc.perform(get("/api/plataforma/logins").param("email", "teste").header("Authorization", sindico))
				.andExpect(status().isForbidden());
	}

	@Test
	void suporteEncerradoAntesDoPrazoDeixaDeValer() throws Exception {
		UUID condominio = cenario.condominio("Residencial");
		String resposta = postJson("/api/plataforma/suporte", admin, """
				{"condominioId": "%s", "motivo": "Conferir a importação da planilha"}""".formatted(condominio))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String suporte = "Bearer " + JsonPath.read(resposta, "$.token");
		String acesso = JsonPath.read(resposta, "$.acessoId");
		mvc.perform(get("/api/unidades").header("Authorization", suporte)).andExpect(status().isOk());

		mvc.perform(post("/api/plataforma/suporte/{id}/encerramento", acesso).header("Authorization", admin))
				.andExpect(status().isNoContent());

		mvc.perform(get("/api/unidades").header("Authorization", suporte)).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/plataforma/suporte").header("Authorization", admin))
				.andExpect(jsonPath("$[?(@.id == '%s')].encerradoEm".formatted(acesso), hasSize(1)))
				.andExpect(jsonPath("$[?(@.id == '%s' && @.encerradoEm == null)]".formatted(acesso), hasSize(0)));
		// O administrador continua logado normalmente.
		mvc.perform(get("/api/plataforma/condominios").header("Authorization", admin)).andExpect(status().isOk());
	}

	@Test
	void historicoDaAssinaturaComQuemFez() throws Exception {
		String id = JsonPath.read(clienteNovo("sindico-" + UUID.randomUUID() + "@teste.com")
				.andReturn().getResponse().getContentAsString(), "$.condominio.id");
		UUID outro = cenario.condominio("Outro cliente");

		assinatura(id, "ESSENCIAL", "ANUAL", "TESTE");
		assinatura(id, "ESSENCIAL", "ANUAL", "ATIVA");
		assinatura(id, "ESSENCIAL", "ANUAL", "ATIVA"); // nada mudou: não entra no histórico
		mvc.perform(post("/api/plataforma/condominios/{id}/suspensao", id).header("Authorization", admin));
		mvc.perform(post("/api/plataforma/condominios/{id}/reativacao", id).header("Authorization", admin));

		mvc.perform(get("/api/plataforma/condominios/{id}/historico", id).header("Authorization", admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].tipo", contains("REATIVACAO", "SUSPENSAO", "MUDANCA_DE_SITUACAO",
						"TROCA_DE_PLANO", "CLIENTE_NOVO")))
				.andExpect(jsonPath("$[2].status").value("ATIVA"))
				.andExpect(jsonPath("$[3].plano").value("ESSENCIAL"))
				.andExpect(jsonPath("$[3].periodicidade").value("ANUAL"))
				.andExpect(jsonPath("$[4].status").value("TESTE"))
				.andExpect(jsonPath("$[4].feitoPor").value(administrador.getEmail()));

		// O histórico de um cliente não mostra nada do outro.
		mvc.perform(get("/api/plataforma/condominios/{id}/historico", outro).header("Authorization", admin))
				.andExpect(jsonPath("$[*].tipo", contains("CLIENTE_NOVO")))
				.andExpect(jsonPath("$[0].feitoPor").value(nullValue()));
	}

	// ----- apoio -----

	private ResultActions clienteNovo(String emailDoSindico) throws Exception {
		LocalDate posse = cenario.hoje().minusDays(30);
		return postJson("/api/plataforma/condominios", admin, """
				{"nome": "Residencial %s", "tipo": "RESIDENCIAL_VERTICAL", "plano": "COMPLETO", "periodicidade": "MENSAL",
				 "sindico": {"nome": "Síndico Antigo", "email": "%s", "inicioMandato": "%s", "fimMandato": "%s"}}"""
				.formatted(UUID.randomUUID(), emailDoSindico, posse, posse.plusYears(1).minusDays(1)))
				.andExpect(status().isCreated());
	}

	private void assinatura(String id, String plano, String periodicidade, String situacao) throws Exception {
		mvc.perform(put("/api/plataforma/condominios/{id}/assinatura", id).header("Authorization", admin)
						.contentType(MediaType.APPLICATION_JSON).content("""
								{"plano": "%s", "periodicidade": "%s", "status": "%s"}""".formatted(plano, periodicidade, situacao)))
				.andExpect(status().isOk());
	}

	private ResultActions login(String email, String senha) throws Exception {
		return postJson("/api/auth/login", null, """
				{"email": "%s", "senha": "%s"}""".formatted(email, senha));
	}

	private static String codigo(String link) {
		return link.split("codigo=")[1];
	}

	private ResultActions postJson(String url, String token, String corpo) throws Exception {
		var pedido = post(url).contentType(MediaType.APPLICATION_JSON).content(corpo);
		if (token != null) {
			pedido.header("Authorization", token);
		}
		return mvc.perform(pedido);
	}

}
