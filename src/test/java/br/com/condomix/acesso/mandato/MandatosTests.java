package br.com.condomix.acesso.mandato;

import br.com.condomix.CenarioDeTeste;
import br.com.condomix.TestcontainersConfiguration;
import br.com.condomix.acesso.Cargo;
import br.com.condomix.acesso.Perfil;
import br.com.condomix.acesso.PerfilAcesso;
import br.com.condomix.acesso.Pessoa;
import br.com.condomix.acesso.TipoUnidade;
import br.com.condomix.acesso.Unidade;
import br.com.condomix.acesso.Usuario;
import br.com.condomix.acesso.persistencia.UnidadeRepository;
import br.com.condomix.compartilhado.TenantAtual;
import com.jayway.jsonpath.JsonPath;
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

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cargos e mandatos: morador-síndico, síndico profissional, sucessão, prorrogação, renúncia e regras. */
@Import({ TestcontainersConfiguration.class, CenarioDeTeste.class })
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MandatosTests {

	@Autowired MockMvc mvc;
	@Autowired CenarioDeTeste cenario;
	@Autowired UnidadeRepository unidades;

	@Test
	void moradorQueESindicoRecebeOsDoisPapeis() throws Exception {
		UUID condominio = cenario.condominio("Residencial");
		Usuario ana = cenario.usuario("ana");
		cenario.perfil(condominio, ana, Perfil.MORADOR);
		Pessoa pessoa = cenario.pessoa(condominio, "Ana", ana);
		cenario.mandato(condominio, pessoa, Cargo.SINDICO, hoje().minusMonths(2), hoje().plusMonths(10));

		eu(cenario.token(ana)).andExpect(jsonPath("$.perfis", containsInAnyOrder("MORADOR", "SINDICO")));
	}

	@Test
	void sucessaoEncerraOAnteriorEPassaOAcesso() throws Exception {
		UUID condominio = cenario.condominio("Residencial");
		Usuario antigo = cenario.comPapel(condominio, Perfil.SINDICO);
		Usuario novo = cenario.usuario("novo");
		Pessoa pessoaNova = cenario.pessoa(condominio, "Novo Síndico", novo);

		registrar(cenario.token(antigo), pessoaNova.getId(), "SINDICO", hoje(), hoje().plusYears(1).minusDays(1))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.situacao").value("VIGENTE"))
				.andExpect(jsonPath("$.ocupante.nome").value("Novo Síndico"));

		mvc.perform(get("/api/mandatos").header("Authorization", cenario.token(novo)))
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[1].situacao").value("ENCERRADO"))
				.andExpect(jsonPath("$[1].motivoEncerramento").value("SUCESSAO"))
				.andExpect(jsonPath("$[1].encerradoEm").value(hoje().minusDays(1).toString()));
		eu(cenario.token(novo)).andExpect(jsonPath("$.perfis", containsInAnyOrder("SINDICO")));
		login(antigo).andExpect(jsonPath("$.condominios", hasSize(0)));
	}

	@Test
	void mandatoVencidoSemSucessorContinuaComAviso() throws Exception {
		UUID condominio = cenario.condominio("Residencial");
		Usuario sindico = cenario.usuario("sindico");
		Pessoa pessoa = cenario.pessoa(condominio, "Síndico", sindico);
		cenario.mandato(condominio, pessoa, Cargo.SINDICO, hoje().minusDays(400), hoje().minusDays(35));
		String token = cenario.token(sindico);

		mvc.perform(get("/api/mandatos").param("somenteComAcesso", "true").header("Authorization", token))
				.andExpect(jsonPath("$[0].situacao").value("PRORROGADO"))
				.andExpect(jsonPath("$[0].aviso").value(containsString("Prorrogado até a posse do sucessor")));
		eu(token).andExpect(jsonPath("$.perfis", containsInAnyOrder("SINDICO")));
	}

	@Test
	void mandatoPertoDoFimAvisaParaConvocarAssembleia() throws Exception {
		UUID condominio = cenario.condominio("Residencial");
		Usuario sindico = cenario.usuario("sindico");
		cenario.mandato(condominio, cenario.pessoa(condominio, "Síndico", sindico), Cargo.SINDICO,
				hoje().minusMonths(11), hoje().plusDays(30));

		mvc.perform(get("/api/mandatos").header("Authorization", cenario.token(sindico)))
				.andExpect(jsonPath("$[0].situacao").value("A_VENCER"))
				.andExpect(jsonPath("$[0].aviso").value(containsString("termina em 30 dias")));
	}

	@Test
	void renunciaRegistradaPelaAdministradoraTiraOAcesso() throws Exception {
		UUID condominio = cenario.condominio("Residencial");
		Usuario sindico = cenario.comPapel(condominio, Perfil.SINDICO);
		String administradora = cenario.token(cenario.comPapel(condominio, Perfil.ADMINISTRADORA));
		String mandatoId = JsonPath.read(mvc.perform(get("/api/mandatos").header("Authorization", administradora))
				.andReturn().getResponse().getContentAsString(), "$[0].id");

		mvc.perform(post("/api/mandatos/{id}/encerramento", mandatoId).header("Authorization", administradora)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"ultimoDia": "%s", "motivo": "RENUNCIA"}""".formatted(hoje().minusDays(1))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.situacao").value("ENCERRADO"))
				.andExpect(jsonPath("$.motivoEncerramento").value("RENUNCIA"));
		login(sindico).andExpect(jsonPath("$.condominioId").value(nullValue()))
				.andExpect(jsonPath("$.condominios", hasSize(0)));
	}

	@Test
	void sindicoProfissionalAtendeVariosCondominios() throws Exception {
		Usuario carla = cenario.usuario("carla");
		for (String nome : new String[] { "Condomínio 1", "Condomínio 2" }) {
			UUID condominio = cenario.condominio(nome);
			cenario.mandato(condominio, cenario.pessoa(condominio, "Carla", carla), Cargo.SINDICO,
					hoje().minusMonths(1), hoje().plusMonths(11));
		}

		login(carla).andExpect(jsonPath("$.condominioId").value(nullValue()))
				.andExpect(jsonPath("$.condominios", hasSize(2)))
				.andExpect(jsonPath("$.condominios[*].perfis[0]", containsInAnyOrder("SINDICO", "SINDICO")));
	}

	@Test
	void regrasDeRegistro() throws Exception {
		UUID condominio = cenario.condominio("Residencial");
		String sindico = cenario.token(cenario.comPapel(condominio, Perfil.SINDICO));
		String morador = cenario.token(cenario.comPapel(condominio, Perfil.MORADOR));
		UUID pessoa = cenario.pessoa(condominio, "Candidato", null).getId();

		registrar(morador, pessoa, "SINDICO", hoje(), hoje().plusYears(1))
				.andExpect(status().isForbidden());
		registrar(sindico, pessoa, "SUBSINDICO", hoje(), hoje().plusYears(2))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("2 anos")));
		registrar(sindico, pessoa, "SINDICO", hoje().minusYears(1), hoje().plusMonths(6))
				.andExpect(status().isConflict());
		mvc.perform(post("/api/mandatos").header("Authorization", sindico).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pessoaId": "%s", "cargo": "SUBSINDICO", "inicio": "%s", "fimPrevisto": "%s",
								 "descontoPercentual": 50}""".formatted(pessoa, hoje(), hoje().plusYears(1))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void subsindicoComDescontoEConselhoComVariosMembros() throws Exception {
		UUID condominio = cenario.condominio("Residencial");
		String sindico = cenario.token(cenario.comPapel(condominio, Perfil.SINDICO));
		UUID unidade = TenantAtual.executarComo(condominio,
				() -> unidades.save(new Unidade(condominio, "B", "202", TipoUnidade.APARTAMENTO))).getId();
		UUID joao = cenario.pessoa(condominio, "João", null).getId();
		UUID maria = cenario.pessoa(condominio, "Maria", null).getId();

		mvc.perform(post("/api/mandatos").header("Authorization", sindico).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pessoaId": "%s", "cargo": "SUBSINDICO", "inicio": "%s", "fimPrevisto": "%s",
								 "descontoPercentual": 50, "unidadeBeneficiadaId": "%s"}"""
								.formatted(joao, hoje(), hoje().plusYears(1), unidade)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.beneficio.descontoPercentual").value(50))
				.andExpect(jsonPath("$.beneficio.unidadeId").value(unidade.toString()));
		registrar(sindico, joao, "CONSELHEIRO", hoje(), hoje().plusYears(1)).andExpect(status().isCreated());
		registrar(sindico, maria, "CONSELHEIRO", hoje(), hoje().plusYears(1)).andExpect(status().isCreated());
		registrar(sindico, maria, "CONSELHEIRO", hoje(), hoje().plusYears(1)).andExpect(status().isConflict());
	}

	@Test
	void outroCondominioNaoVeNemUsaOsDados() throws Exception {
		UUID a = cenario.condominio("A");
		UUID b = cenario.condominio("B");
		String sindicoA = cenario.token(cenario.comPapel(a, Perfil.SINDICO));
		String sindicoB = cenario.token(cenario.comPapel(b, Perfil.SINDICO));
		UUID pessoaDeA = cenario.pessoa(a, "Pessoa de A", null).getId();
		String mandatoDeA = JsonPath.read(mvc.perform(get("/api/mandatos").header("Authorization", sindicoA))
				.andReturn().getResponse().getContentAsString(), "$[0].id");
		String ataDeA = JsonPath.read(mvc.perform(multipart("/api/documentos")
						.file(new MockMultipartFile("arquivo", "ata.pdf", "application/pdf", "%PDF-1.7".getBytes()))
						.param("titulo", "Ata de eleição").param("categoria", "ATA_ASSEMBLEIA")
						.header("Authorization", sindicoA))
				.andReturn().getResponse().getContentAsString(), "$.id");
		UUID pessoaDeB = cenario.pessoa(b, "Pessoa de B", null).getId();

		mvc.perform(get("/api/mandatos").header("Authorization", sindicoB)).andExpect(jsonPath("$", hasSize(1)));
		mvc.perform(get("/api/mandatos/{id}", mandatoDeA).header("Authorization", sindicoB))
				.andExpect(status().isNotFound());
		registrar(sindicoB, pessoaDeA, "CONSELHEIRO", hoje(), hoje().plusYears(1)).andExpect(status().isNotFound());
		mvc.perform(post("/api/mandatos").header("Authorization", sindicoB).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pessoaId": "%s", "cargo": "CONSELHEIRO", "inicio": "%s", "fimPrevisto": "%s",
								 "ataEleicaoId": "%s"}""".formatted(pessoaDeB, hoje(), hoje().plusYears(1), ataDeA)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("ata")));
	}

	@Test
	void cargoEleitoNaoPodeSerGravadoComoPerfil() {
		Usuario usuario = new Usuario("x@teste.com", "{noop}x");
		assertThatThrownBy(() -> new PerfilAcesso(UUID.randomUUID(), usuario, Perfil.SINDICO, null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	// ----- apoio -----

	private LocalDate hoje() {
		return cenario.hoje();
	}

	private ResultActions registrar(String token, UUID pessoa, String cargo, LocalDate inicio, LocalDate fim)
			throws Exception {
		return mvc.perform(post("/api/mandatos").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"pessoaId": "%s", "cargo": "%s", "inicio": "%s", "fimPrevisto": "%s"}"""
						.formatted(pessoa, cargo, inicio, fim)));
	}

	private ResultActions eu(String token) throws Exception {
		return mvc.perform(get("/api/eu").header("Authorization", token)).andExpect(status().isOk());
	}

	private ResultActions login(Usuario usuario) throws Exception {
		return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "%s", "senha": "%s"}""".formatted(usuario.getEmail(), CenarioDeTeste.SENHA)));
	}

}
