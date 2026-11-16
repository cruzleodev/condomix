package br.com.condomix.acesso.convite;

import br.com.condomix.acesso.CredenciaisInvalidasException;
import br.com.condomix.acesso.Perfil;
import br.com.condomix.acesso.PerfilAcesso;
import br.com.condomix.acesso.Pessoa;
import br.com.condomix.acesso.Usuario;
import br.com.condomix.acesso.persistencia.ConviteRepository;
import br.com.condomix.acesso.persistencia.PerfilAcessoRepository;
import br.com.condomix.acesso.persistencia.UsuarioRepository;
import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.plataforma.ConsultaDeCondominios;
import br.com.condomix.plataforma.ResumoCondominio;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Caso de uso: quem recebeu o convite vê do que se trata e define a senha (ou confirma a senha do
 * login que já tem). Não usa {@code @Transactional}: o condomínio só é conhecido depois de achar o convite,
 * e o tenant precisa estar definido antes de a transação começar (ver {@link TenantAtual}).
 */
@Service
public class AceiteDeConvites {

	public static final int TAMANHO_MINIMO_DA_SENHA = 8;

	private final ConviteRepository convites;

	private final UsuarioRepository usuarios;

	private final PerfilAcessoRepository perfis;

	private final ConsultaDeCondominios condominios;

	private final PasswordEncoder senhas;

	private final TransactionTemplate transacao;

	private final Clock relogio;

	public AceiteDeConvites(ConviteRepository convites, UsuarioRepository usuarios, PerfilAcessoRepository perfis,
			ConsultaDeCondominios condominios, PasswordEncoder senhas, TransactionTemplate transacao, Clock relogio) {
		this.convites = convites;
		this.usuarios = usuarios;
		this.perfis = perfis;
		this.condominios = condominios;
		this.senhas = senhas;
		this.transacao = transacao;
		this.relogio = relogio;
	}

	/** O que a tela de aceite mostra antes de pedir a senha. */
	public record InformacoesDoConvite(String condominio, String pessoa, String email, Perfil perfil,
			Instant expiraEm, boolean jaTemLogin) {
	}

	public InformacoesDoConvite consultar(String codigo) {
		Convite convite = valido(codigo);
		String condominio = condominios.ativosEntre(List.of(convite.getTenantId())).stream()
				.map(ResumoCondominio::nome).findFirst()
				.orElseThrow(() -> new NaoEncontradoException("Convite inválido ou expirado"));
		return new InformacoesDoConvite(condominio, convite.getPessoa().getNome(), convite.getEmail(),
				convite.getPerfil(), convite.getExpiraEm(), usuarios.buscarPorEmail(convite.getEmail()).isPresent());
	}

	/**
	 * Aceita o convite. Sem login com esse e-mail: cria o login com a {@code senha}. Com login: a
	 * {@code senha} tem que ser a atual (prova que é a mesma pessoa). Devolve o e-mail do login.
	 */
	public String aceitar(String codigo, String senha) {
		Convite encontrado = valido(codigo);
		UUID condominio = encontrado.getTenantId();
		return TenantAtual.executarComo(condominio, () -> transacao.execute(status -> {
			Convite convite = convites.findById(encontrado.getId()).orElseThrow();
			Usuario usuario = loginDoConvidado(convite.getEmail(), senha);
			Pessoa pessoa = convite.getPessoa();
			if (pessoa.getUsuarioId() != null && !pessoa.getUsuarioId().equals(usuario.getId())) {
				throw RegraDeNegocioException.conflito("Esta pessoa já está ligada a outro login");
			}
			pessoa.vincularUsuario(usuario.getId());
			if (convite.getPerfil() != null && !perfis.existsByUsuarioIdAndPerfil(usuario.getId(), convite.getPerfil())) {
				perfis.save(new PerfilAcesso(condominio, usuario, convite.getPerfil(), pessoa));
			}
			convite.aceitar(usuario.getId(), Instant.now(relogio));
			return usuario.getEmail();
		}));
	}

	private Usuario loginDoConvidado(String email, String senha) {
		var existente = usuarios.buscarPorEmail(email);
		if (existente.isPresent()) {
			if (senha == null || !senhas.matches(senha, existente.get().getSenhaHash()) || !existente.get().isAtivo()) {
				throw new CredenciaisInvalidasException();
			}
			return existente.get();
		}
		if (senha == null || senha.length() < TAMANHO_MINIMO_DA_SENHA) {
			throw RegraDeNegocioException.invalido("A senha precisa ter pelo menos " + TAMANHO_MINIMO_DA_SENHA + " caracteres");
		}
		return usuarios.save(new Usuario(email, senhas.encode(senha)));
	}

	/** Mesma resposta para código inexistente, usado, cancelado ou expirado: não revela qual. */
	private Convite valido(String codigo) {
		if (codigo == null || codigo.isBlank()) {
			throw new NaoEncontradoException("Convite inválido ou expirado");
		}
		String hash = CodigoDeConvite.impressaoDigital(codigo.strip());
		return TenantAtual.comoSistema(() -> convites.porCodigo(hash))
				.filter(c -> c.podeSerUsadoEm(Instant.now(relogio)))
				.orElseThrow(() -> new NaoEncontradoException("Convite inválido ou expirado"));
	}

}
