package br.com.condomix.documentos;

import br.com.condomix.TestcontainersConfiguration;
import br.com.condomix.acesso.Perfil;
import br.com.condomix.acesso.PerfilAcesso;
import br.com.condomix.acesso.Usuario;
import br.com.condomix.acesso.persistencia.PerfilAcessoRepository;
import br.com.condomix.acesso.persistencia.UsuarioRepository;
import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.plataforma.CadastroDeCondominios;
import br.com.condomix.plataforma.Periodicidade;
import br.com.condomix.plataforma.TipoCondominio;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A API de documentos de ponta a ponta: publicação, permissões, busca, download e isolamento. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
class DocumentosTests {

	private static final String SENHA = "senha-de-teste";

	private static final byte[] PDF = "%PDF-1.7\nconteúdo de teste".getBytes(StandardCharsets.UTF_8);

	@Autowired MockMvc mvc;
	@Autowired CadastroDeCondominios cadastroDeCondominios;
	@Autowired UsuarioRepository usuarios;
	@Autowired PerfilAcessoRepository perfis;
	@Autowired PasswordEncoder senhas;
	@Autowired ApplicationEvents eventos;

	String sindico;
	String morador;
	String sindicoDeOutroCondominio;

	@BeforeEach
	void prepararCondominios() throws Exception {
		UUID condominio = cadastroDeCondominios.cadastrar("Residencial Teste", TipoCondominio.RESIDENCIAL_VERTICAL,
				"ESSENCIAL", Periodicidade.MENSAL);
		UUID outro = cadastroDeCondominios.cadastrar("Outro Condomínio", TipoCondominio.COMERCIAL,
				"ESSENCIAL", Periodicidade.MENSAL);
		sindico = tokenDe(condominio, Perfil.SINDICO);
		morador = tokenDe(condominio, Perfil.MORADOR);
		sindicoDeOutroCondominio = tokenDe(outro, Perfil.SINDICO);
	}

	@Test
	void sindicoPublicaEMoradorVeEBaixa() throws Exception {
		String id = idDe(publicar(sindico, "Ata da assembleia de março", "ATA_ASSEMBLEIA", arquivo("ata.pdf", PDF))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.categoriaRotulo").value("Ata de assembleia"))
				.andExpect(jsonPath("$.arquivo.tipo").value("application/pdf"))
				.andExpect(jsonPath("$.arquivo.tamanho").value(PDF.length))
				.andExpect(jsonPath("$.arquivo.sha256").value(hasLength64())));

		mvc.perform(get("/api/documentos").header("Authorization", morador))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.itens[0].titulo").value("Ata da assembleia de março"));

		mvc.perform(get("/api/documentos/{id}/arquivo", id).header("Authorization", morador))
				.andExpect(status().isOk())
				.andExpect(content().contentType("application/pdf"))
				.andExpect(header().string("Content-Disposition", containsString("ata.pdf")))
				.andExpect(content().bytes(PDF));

		assertThat(eventos.stream(DocumentoPublicado.class))
				.singleElement().satisfies(e -> assertThat(e.documentoId().toString()).isEqualTo(id));
	}

	@Test
	void moradorNaoPublicaNemRemove() throws Exception {
		publicar(morador, "Tentativa", "OUTRO", arquivo("a.pdf", PDF)).andExpect(status().isForbidden());

		String id = idDe(publicar(sindico, "Regimento", "REGIMENTO_INTERNO", arquivo("r.pdf", PDF)));
		mvc.perform(delete("/api/documentos/{id}", id).header("Authorization", morador))
				.andExpect(status().isForbidden());
	}

	@Test
	void recusaArquivoComTipoFalsoOuNaoAceito() throws Exception {
		publicar(sindico, "PDF falso", "OUTRO", arquivo("virus.pdf", "MZ executável".getBytes()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("Aceitos")));
		publicar(sindico, "Executável", "OUTRO", arquivo("programa.exe", PDF))
				.andExpect(status().isBadRequest());
		publicar(sindico, "", "OUTRO", arquivo("a.pdf", PDF))
				.andExpect(status().isBadRequest());
	}

	@Test
	void buscaIgnoraAcentosEEncontraPelaDescricao() throws Exception {
		publicar(sindico, "Convenção do condomínio", "CONVENCAO", arquivo("c.pdf", PDF));
		mvc.perform(multipart("/api/documentos").file(arquivo("ata.pdf", PDF))
				.param("titulo", "Ata de março").param("categoria", "ATA_ASSEMBLEIA")
				.param("descricao", "Aprovação da reforma da piscina").param("dataReferencia", "2026-03-15")
				.header("Authorization", sindico)).andExpect(status().isCreated());

		buscar(morador, "convencao").andExpect(jsonPath("$.itens", hasSize(1)))
				.andExpect(jsonPath("$.itens[0].categoria").value("CONVENCAO"));
		buscar(morador, "piscina").andExpect(jsonPath("$.itens[0].titulo").value("Ata de março"));
		buscar(morador, "reformas").andExpect(jsonPath("$.total").value(1)); // "reformas" acha "reforma"
		buscar(morador, "churrasqueira").andExpect(jsonPath("$.total").value(0));
		mvc.perform(get("/api/documentos").param("categoria", "CONVENCAO").header("Authorization", morador))
				.andExpect(jsonPath("$.total").value(1));
	}

	@Test
	void outroCondominioNaoVeNemBaixa() throws Exception {
		String id = idDe(publicar(sindico, "Convenção secreta", "CONVENCAO", arquivo("c.pdf", PDF)));

		mvc.perform(get("/api/documentos").header("Authorization", sindicoDeOutroCondominio))
				.andExpect(jsonPath("$.total").value(0));
		buscar(sindicoDeOutroCondominio, "convencao").andExpect(jsonPath("$.total").value(0));
		mvc.perform(get("/api/documentos/{id}", id).header("Authorization", sindicoDeOutroCondominio))
				.andExpect(status().isNotFound());
		mvc.perform(get("/api/documentos/{id}/arquivo", id).header("Authorization", sindicoDeOutroCondominio))
				.andExpect(status().isNotFound());
		mvc.perform(delete("/api/documentos/{id}", id).header("Authorization", sindicoDeOutroCondominio))
				.andExpect(status().isNotFound());
	}

	@Test
	void sindicoCorrigeERemove() throws Exception {
		String id = idDe(publicar(sindico, "Ata errada", "OUTRO", arquivo("a.pdf", PDF)));

		mvc.perform(put("/api/documentos/{id}", id).header("Authorization", sindico)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"titulo": "Ata de abril", "categoria": "ATA_ASSEMBLEIA", "dataReferencia": "2026-04-10"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.titulo").value("Ata de abril"))
				.andExpect(jsonPath("$.dataReferencia").value("2026-04-10"));

		mvc.perform(delete("/api/documentos/{id}", id).header("Authorization", sindico))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/documentos/{id}", id).header("Authorization", morador))
				.andExpect(status().isNotFound());
		mvc.perform(get("/api/documentos").header("Authorization", morador))
				.andExpect(jsonPath("$.total").value(0));
	}

	@Test
	void listaCategorias() throws Exception {
		mvc.perform(get("/api/documentos/categorias").header("Authorization", morador))
				.andExpect(jsonPath("$", hasSize(Categoria.values().length)))
				.andExpect(jsonPath("$[0].rotulo").value("Convenção"));
	}

	// ----- apoio -----

	private ResultActions publicar(String token, String titulo, String categoria, MockMultipartFile arquivo)
			throws Exception {
		return mvc.perform(multipart("/api/documentos").file(arquivo)
				.param("titulo", titulo).param("categoria", categoria)
				.header("Authorization", token));
	}

	private ResultActions buscar(String token, String termo) throws Exception {
		return mvc.perform(get("/api/documentos").param("busca", termo).header("Authorization", token))
				.andExpect(status().isOk());
	}

	private static MockMultipartFile arquivo(String nome, byte[] conteudo) {
		return new MockMultipartFile("arquivo", nome, "application/octet-stream", conteudo);
	}

	private static String idDe(ResultActions resposta) throws Exception {
		return JsonPath.read(resposta.andReturn().getResponse().getContentAsString(), "$.id");
	}

	private static org.hamcrest.Matcher<String> hasLength64() {
		return org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}");
	}

	/** Cria um usuário com o perfil no condomínio, faz o login e devolve "Bearer <token>". */
	private String tokenDe(UUID condominio, Perfil perfil) throws Exception {
		String email = perfil.name().toLowerCase() + "-" + UUID.randomUUID() + "@teste.com";
		Usuario usuario = usuarios.save(new Usuario(email, senhas.encode(SENHA)));
		TenantAtual.executarComo(condominio, () -> perfis.save(new PerfilAcesso(condominio, usuario, perfil, null)));
		String resposta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "%s", "senha": "%s"}""".formatted(email, SENHA)))
				.andReturn().getResponse().getContentAsString();
		return "Bearer " + JsonPath.read(resposta, "$.token");
	}

}
