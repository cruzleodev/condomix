package br.com.condomix.acesso;

import br.com.condomix.acesso.persistencia.UsuarioRepository;
import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.plataforma.ConsultaDeCondominios;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Sem {@code @Transactional} na classe: os papéis de cada login são lidos em modo sistema
 * ({@link PapeisDoUsuario}), e o tenant é definido antes de cada transação.
 */
@Service
class GestaoDeLogins implements LoginsDaPlataforma {

	private final UsuarioRepository usuarios;

	private final PapeisDoUsuario papeis;

	private final ConsultaDeCondominios condominios;

	private final TransactionTemplate transacao;

	private final Clock relogio;

	GestaoDeLogins(UsuarioRepository usuarios, PapeisDoUsuario papeis, ConsultaDeCondominios condominios,
			TransactionTemplate transacao, Clock relogio) {
		this.usuarios = usuarios;
		this.papeis = papeis;
		this.condominios = condominios;
		this.transacao = transacao;
		this.relogio = relogio;
	}

	@Override
	public List<LoginNaPlataforma> buscar(String parteDoEmail) {
		String termo = parteDoEmail == null ? "" : parteDoEmail.strip().toLowerCase(Locale.ROOT);
		if (termo.length() < 3) {
			throw RegraDeNegocioException.invalido("Digite pelo menos 3 letras do e-mail");
		}
		return usuarios.findTop20ByEmailContainingOrderByEmail(termo).stream().map(this::paraPainel).toList();
	}

	@Override
	public LoginNaPlataforma bloquear(UUID usuarioId, String motivo, UUID bloqueadoPor) {
		Usuario usuario = transacao.execute(s -> {
			Usuario u = buscarUsuario(usuarioId);
			if (u.isBloqueado()) {
				throw RegraDeNegocioException.conflito("Este login já está bloqueado");
			}
			try {
				u.bloquear(motivo, bloqueadoPor, Instant.now(relogio));
			}
			catch (IllegalArgumentException e) {
				throw RegraDeNegocioException.invalido(e.getMessage());
			}
			return u;
		});
		return paraPainel(usuario);
	}

	@Override
	public LoginNaPlataforma desbloquear(UUID usuarioId) {
		Usuario usuario = transacao.execute(s -> {
			Usuario u = buscarUsuario(usuarioId);
			try {
				u.desbloquear();
			}
			catch (IllegalStateException e) {
				throw RegraDeNegocioException.conflito(e.getMessage());
			}
			return u;
		});
		return paraPainel(usuario);
	}

	@Override
	public Map<UUID, String> emails(Collection<UUID> usuarioIds) {
		return usuarios.findAllById(usuarioIds).stream().collect(Collectors.toMap(Usuario::getId, Usuario::getEmail));
	}

	private Usuario buscarUsuario(UUID id) {
		return usuarios.findById(id).orElseThrow(() -> new NaoEncontradoException("Login não encontrado"));
	}

	private LoginNaPlataforma paraPainel(Usuario u) {
		var papeisPorCondominio = papeis.porCondominio(u.getId());
		List<AcessoDoLogin> acessos = condominios.ativosEntre(papeisPorCondominio.keySet()).stream()
				.map(c -> new AcessoDoLogin(c.id(), c.nome(), papeisPorCondominio.get(c.id())))
				.sorted(Comparator.comparing(AcessoDoLogin::nome, String.CASE_INSENSITIVE_ORDER))
				.toList();
		return new LoginNaPlataforma(u.getId(), u.getEmail(), u.isBloqueado(), u.getMotivoBloqueio(), u.getBloqueadoEm(),
				u.isAdminPlataforma(), u.getUltimoAcesso(), acessos);
	}

}
