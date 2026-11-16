package br.com.condomix.acesso;

import br.com.condomix.acesso.persistencia.UsuarioRepository;
import br.com.condomix.plataforma.ConsultaDeCondominios;
import br.com.condomix.plataforma.ResumoCondominio;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Login e seleção de condomínio.
 *
 * <p>Fluxo: o usuário entra com e-mail e senha. Se ele tem acesso a um só condomínio, o token já sai
 * com esse condomínio. Se tem mais de um, o token sai sem condomínio, junto com a lista; o app então
 * chama {@link #selecionarCondominio} para receber o token do condomínio escolhido.
 *
 * <p>Os métodos não são {@code @Transactional} de propósito: a busca dos perfis precisa rodar em modo
 * sistema (todos os condomínios), e o tenant é lido pelo Hibernate no início de cada transação.
 */
@Service
public class AutenticacaoService {

	private final UsuarioRepository usuarios;

	private final PapeisDoUsuario papeis;

	private final ConsultaDeCondominios condominios;

	private final PasswordEncoder senhas;

	private final EmissorDeToken emissor;

	private final Clock relogio;

	/** Usado quando o e-mail não existe, para a resposta demorar o mesmo que uma senha errada. */
	private final String hashFicticio;

	AutenticacaoService(UsuarioRepository usuarios, PapeisDoUsuario papeis, ConsultaDeCondominios condominios,
			PasswordEncoder senhas, EmissorDeToken emissor, Clock relogio) {
		this.usuarios = usuarios;
		this.papeis = papeis;
		this.condominios = condominios;
		this.senhas = senhas;
		this.emissor = emissor;
		this.relogio = relogio;
		this.hashFicticio = senhas.encode(UUID.randomUUID().toString());
	}

	public Sessao entrar(String email, String senha) {
		Usuario usuario = usuarios.buscarPorEmail(email).orElse(null);
		if (usuario == null) {
			senhas.matches(senha, hashFicticio);
			throw new CredenciaisInvalidasException();
		}
		if (!senhas.matches(senha, usuario.getSenhaHash()) || !usuario.isAtivo()) {
			throw new CredenciaisInvalidasException();
		}
		usuario.registrarAcesso(Instant.now(relogio));
		usuarios.save(usuario);

		List<Sessao.CondominioDisponivel> disponiveis = condominiosDo(usuario);
		UUID selecionado = disponiveis.size() == 1 ? disponiveis.getFirst().id() : null;
		return montarSessao(usuario, disponiveis, selecionado);
	}

	public Sessao selecionarCondominio(UUID usuarioId, UUID condominioId) {
		Usuario usuario = usuarios.findById(usuarioId)
				.filter(Usuario::isAtivo)
				.orElseThrow(CredenciaisInvalidasException::new);
		List<Sessao.CondominioDisponivel> disponiveis = condominiosDo(usuario);
		if (disponiveis.stream().noneMatch(c -> c.id().equals(condominioId))) {
			throw new AcessoAoCondominioNegadoException();
		}
		return montarSessao(usuario, disponiveis, condominioId);
	}

	/** Condomínios ativos em que o usuário tem algum papel hoje, com os papéis de cada um. */
	private List<Sessao.CondominioDisponivel> condominiosDo(Usuario usuario) {
		Map<UUID, Set<Perfil>> papeis = this.papeis.porCondominio(usuario.getId());
		return condominios.ativosEntre(papeis.keySet()).stream()
				.map((ResumoCondominio c) -> new Sessao.CondominioDisponivel(c.id(), c.nome(), papeis.get(c.id())))
				.sorted(Comparator.comparing(Sessao.CondominioDisponivel::nome))
				.toList();
	}

	private Sessao montarSessao(Usuario usuario, List<Sessao.CondominioDisponivel> disponiveis, UUID selecionado) {
		Set<Perfil> perfisNoSelecionado = EnumSet.noneOf(Perfil.class);
		disponiveis.stream()
				.filter(c -> c.id().equals(selecionado))
				.findFirst()
				.ifPresent(c -> perfisNoSelecionado.addAll(c.perfis()));
		if (usuario.isAdminPlataforma()) {
			perfisNoSelecionado.add(Perfil.ADMIN_PLATAFORMA);
		}
		var token = emissor.emitir(usuario, selecionado, perfisNoSelecionado);
		return new Sessao(token.valor(), "Bearer", token.expiraEm(), selecionado, perfisNoSelecionado, disponiveis);
	}

}
