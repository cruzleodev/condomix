package br.com.condomix;

import br.com.condomix.acesso.Cargo;
import br.com.condomix.acesso.Perfil;
import br.com.condomix.acesso.PerfilAcesso;
import br.com.condomix.acesso.Pessoa;
import br.com.condomix.acesso.Usuario;
import br.com.condomix.acesso.mandato.Mandato;
import br.com.condomix.acesso.mandato.RegistroDeMandatos;
import br.com.condomix.acesso.persistencia.PerfilAcessoRepository;
import br.com.condomix.acesso.persistencia.PessoaRepository;
import br.com.condomix.acesso.persistencia.UsuarioRepository;
import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.plataforma.CadastroDeCondominios;
import br.com.condomix.plataforma.Periodicidade;
import br.com.condomix.plataforma.TipoCondominio;
import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Monta cenários para os testes: condomínios, usuários, perfis, mandatos e login.
 * Use com {@code @Import(CenarioDeTeste.class)}. Tudo é criado com nomes únicos, então os testes não se atrapalham.
 */
@TestComponent
public class CenarioDeTeste {

	public static final String SENHA = "senha-de-teste";

	private final CadastroDeCondominios condominios;
	private final UsuarioRepository usuarios;
	private final PessoaRepository pessoas;
	private final PerfilAcessoRepository perfis;
	private final RegistroDeMandatos mandatos;
	private final PasswordEncoder senhas;
	private final ObjectProvider<MockMvc> mvc;
	private final Clock relogio;

	CenarioDeTeste(CadastroDeCondominios condominios, UsuarioRepository usuarios, PessoaRepository pessoas,
			PerfilAcessoRepository perfis, RegistroDeMandatos mandatos, PasswordEncoder senhas,
			ObjectProvider<MockMvc> mvc, Clock relogio) {
		this.condominios = condominios;
		this.usuarios = usuarios;
		this.pessoas = pessoas;
		this.perfis = perfis;
		this.mandatos = mandatos;
		this.senhas = senhas;
		this.mvc = mvc;
		this.relogio = relogio;
	}

	public LocalDate hoje() {
		return LocalDate.now(relogio);
	}

	public UUID condominio(String nome) {
		return condominios.cadastrar(nome, TipoCondominio.RESIDENCIAL_VERTICAL, "COMPLETO", Periodicidade.MENSAL);
	}

	public Usuario usuario(String apelido) {
		String email = apelido.toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID() + "@teste.com";
		return usuarios.save(new Usuario(email, senhas.encode(SENHA)));
	}

	/** Pessoa do condomínio; com {@code usuario}, ela tem login. */
	public Pessoa pessoa(UUID condominio, String nome, Usuario usuario) {
		return TenantAtual.executarComo(condominio, () -> {
			Pessoa pessoa = new Pessoa(condominio, nome);
			if (usuario != null) {
				pessoa.vincularUsuario(usuario.getId());
			}
			return pessoas.save(pessoa);
		});
	}

	public void perfil(UUID condominio, Usuario usuario, Perfil perfil) {
		TenantAtual.executarComo(condominio, () -> perfis.save(new PerfilAcesso(condominio, usuario, perfil, null)));
	}

	public Mandato mandato(UUID condominio, Pessoa pessoa, Cargo cargo, LocalDate inicio, LocalDate fimPrevisto) {
		return TenantAtual.executarComo(condominio, () -> mandatos.registrar(new RegistroDeMandatos.NovoMandato(
				pessoa.getId(), cargo, inicio, fimPrevisto, null, null, null), null));
	}

	/** Usuário com o papel no condomínio: cargo eleito vira mandato vigente; os demais, perfil. */
	public Usuario comPapel(UUID condominio, Perfil papel) {
		Usuario usuario = usuario(papel.name());
		if (papel.atribuidoPorMandato()) {
			Pessoa pessoa = pessoa(condominio, "Pessoa " + papel.name(), usuario);
			mandato(condominio, pessoa, Cargo.valueOf(papel.name()), hoje().minusDays(30), hoje().plusDays(300));
		}
		else {
			perfil(condominio, usuario, papel);
		}
		return usuario;
	}

	/** Faz o login e devolve "Bearer &lt;token&gt;". Com um condomínio só, o token já vem com ele. */
	public String token(Usuario usuario) throws Exception {
		String resposta = mvc.getObject().perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "%s", "senha": "%s"}""".formatted(usuario.getEmail(), SENHA)))
				.andReturn().getResponse().getContentAsString();
		return "Bearer " + JsonPath.read(resposta, "$.token");
	}

	/** Login seguido da escolha do condomínio. */
	public String token(Usuario usuario, UUID condominio) throws Exception {
		String resposta = mvc.getObject().perform(post("/api/auth/condominio")
						.header("Authorization", token(usuario)).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"condominioId": "%s"}""".formatted(condominio)))
				.andReturn().getResponse().getContentAsString();
		return "Bearer " + JsonPath.read(resposta, "$.token");
	}

}
