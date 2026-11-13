package br.com.condomix;

import br.com.condomix.acesso.Perfil;
import br.com.condomix.acesso.PerfilAcesso;
import br.com.condomix.acesso.PerfilAcessoRepository;
import br.com.condomix.acesso.Usuario;
import br.com.condomix.acesso.UsuarioRepository;
import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.plataforma.Condominio;
import br.com.condomix.plataforma.CondominioRepository;
import br.com.condomix.plataforma.TipoCondominio;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Login, seleção de condomínio e as regras de acesso da API, de ponta a ponta. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AutenticacaoTests {

	private static final String SENHA = "senha-de-teste";

	@Autowired MockMvc mvc;
	@Autowired UsuarioRepository usuarios;
	@Autowired PerfilAcessoRepository perfis;
	@Autowired CondominioRepository condominios;
	@Autowired PasswordEncoder senhas;

	UUID condominioA;
	UUID condominioB;
	UUID condominioDeOutros;
	String emailMorador;
	String emailSindico;

	@BeforeEach
	void prepararDados() {
		condominioA = condominios.save(new Condominio("A - Residencial", TipoCondominio.RESIDENCIAL_VERTICAL)).getId();
		condominioB = condominios.save(new Condominio("B - Comercial", TipoCondominio.COMERCIAL)).getId();
		condominioDeOutros = condominios.save(new Condominio("C - Outro", TipoCondominio.COMERCIAL)).getId();

		emailMorador = "morador-" + UUID.randomUUID() + "@teste.com";
		emailSindico = "sindico-" + UUID.randomUUID() + "@teste.com";
		Usuario morador = usuarios.save(new Usuario(emailMorador, senhas.encode(SENHA)));
		Usuario sindico = usuarios.save(new Usuario(emailSindico, senhas.encode(SENHA)));

		TenantAtual.executarComo(condominioA, () -> {
			perfis.save(new PerfilAcesso(condominioA, morador, Perfil.MORADOR, null));
			perfis.save(new PerfilAcesso(condominioA, sindico, Perfil.SINDICO, null));
			return null;
		});
		TenantAtual.executarComo(condominioB, () -> perfis.save(new PerfilAcesso(condominioB, sindico, Perfil.SINDICO, null)));
	}

	@Test
	void senhaErradaDevolve401() throws Exception {
		login(emailMorador, "errada").andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos"));
		login("nao-existe@teste.com", SENHA).andExpect(status().isUnauthorized());
	}

	@Test
	void dadosInvalidosDevolvem400() throws Exception {
		login("isso-nao-e-email", "").andExpect(status().isBadRequest());
	}

	@Test
	void semTokenDevolve401() throws Exception {
		mvc.perform(get("/api/eu")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/eu").header("Authorization", "Bearer token-falso")).andExpect(status().isUnauthorized());
	}

	@Test
	void usuarioDeUmCondominioEntraDireto() throws Exception {
		String token = token(login(emailMorador.toUpperCase(), SENHA)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tipo").value("Bearer"))
				.andExpect(jsonPath("$.condominioId").value(condominioA.toString()))
				.andExpect(jsonPath("$.perfis", contains("MORADOR"))));

		mvc.perform(get("/api/eu").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(emailMorador))
				.andExpect(jsonPath("$.condominioId").value(condominioA.toString()))
				.andExpect(jsonPath("$.perfis", contains("MORADOR")));
	}

	@Test
	void usuarioDeVariosCondominiosEscolheUm() throws Exception {
		String tokenSemCondominio = token(login(emailSindico, SENHA)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.condominioId").value(nullValue()))
				.andExpect(jsonPath("$.condominios", hasSize(2)))
				.andExpect(jsonPath("$.condominios[0].nome").value("A - Residencial")));

		// Sem condomínio, só as rotas de autenticação funcionam.
		mvc.perform(get("/api/qualquer-coisa").header("Authorization", "Bearer " + tokenSemCondominio))
				.andExpect(status().isForbidden());

		String tokenB = token(escolher(tokenSemCondominio, condominioB)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.condominioId").value(condominioB.toString()))
				.andExpect(jsonPath("$.perfis", contains("SINDICO"))));

		mvc.perform(get("/api/eu").header("Authorization", "Bearer " + tokenB))
				.andExpect(jsonPath("$.condominioId").value(condominioB.toString()));
		// Com condomínio, a rota passa pela segurança (e cai no 404 porque ainda não existe).
		mvc.perform(get("/api/qualquer-coisa").header("Authorization", "Bearer " + tokenB))
				.andExpect(status().isNotFound());
	}

	@Test
	void naoEscolheCondominioSemAcesso() throws Exception {
		String token = token(login(emailSindico, SENHA));
		escolher(token, condominioDeOutros).andExpect(status().isForbidden());
	}

	private ResultActions login(String email, String senha) throws Exception {
		return mvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "%s", "senha": "%s"}""".formatted(email, senha)));
	}

	private ResultActions escolher(String token, UUID condominioId) throws Exception {
		return mvc.perform(post("/api/auth/condominio")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"condominioId": "%s"}""".formatted(condominioId)));
	}

	private static String token(ResultActions resposta) throws Exception {
		return JsonPath.read(resposta.andReturn().getResponse().getContentAsString(), "$.token");
	}

}
