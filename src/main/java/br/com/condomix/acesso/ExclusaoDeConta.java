package br.com.condomix.acesso;

import br.com.condomix.acesso.persistencia.PerfilAcessoRepository;
import br.com.condomix.acesso.persistencia.PessoaRepository;
import br.com.condomix.acesso.persistencia.UsuarioRepository;
import br.com.condomix.compartilhado.Cofre;
import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.compartilhado.TenantAtual;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Caso de uso: a pessoa exclui a própria conta (a App Store exige que dê para fazer isso pelo app). O login é apagado
 * e a pessoa sai de todos os condomínios; o cadastro dela em cada condomínio (e o histórico financeiro) fica com o
 * condomínio, que é o responsável por esses dados. Pede a senha para confirmar.
 *
 * <p>Sem {@code @Transactional} na classe: cada condomínio é mexido dentro dele (o tenant vem antes da transação).
 */
@Service
class ExclusaoDeConta {

	private final UsuarioRepository usuarios;

	private final PessoaRepository pessoas;

	private final PerfilAcessoRepository perfis;

	private final PasswordEncoder senhas;

	private final ApplicationEventPublisher eventos;

	private final TransactionTemplate transacao;

	private final Clock relogio;

	ExclusaoDeConta(UsuarioRepository usuarios, PessoaRepository pessoas, PerfilAcessoRepository perfis, PasswordEncoder senhas,
			ApplicationEventPublisher eventos, TransactionTemplate transacao, Clock relogio) {
		this.usuarios = usuarios;
		this.pessoas = pessoas;
		this.perfis = perfis;
		this.senhas = senhas;
		this.eventos = eventos;
		this.transacao = transacao;
		this.relogio = relogio;
	}

	void excluir(UUID usuarioId, String senha) {
		Usuario usuario = transacao.execute(s -> usuarios.findById(usuarioId).filter(Usuario::isAtivo)
				.orElseThrow(() -> new NaoEncontradoException("Login não encontrado")));
		if (senha == null || !senhas.matches(senha, usuario.getSenhaHash())) {
			throw RegraDeNegocioException.invalido("A senha não confere.");
		}
		if (usuario.isAdminPlataforma()) {
			throw RegraDeNegocioException.conflito("O administrador da plataforma não pode excluir o login pelo app.");
		}
		// Só os ids dos condomínios em que ele está; depois, cada um é mexido dentro dele.
		Set<UUID> condominios = new LinkedHashSet<>(TenantAtual.comoSistema(() -> transacao.execute(s -> {
			List<UUID> ids = new java.util.ArrayList<>(pessoas.condominiosDoUsuario(usuarioId));
			perfis.findByUsuarioIdAndAtivoTrue(usuarioId).forEach(p -> ids.add(p.getTenantId()));
			return ids;
		})));
		for (UUID condominio : condominios) {
			TenantAtual.executarComo(condominio, () -> {
				transacao.executeWithoutResult(s -> {
					pessoas.findByUsuarioId(usuarioId).forEach(Pessoa::desvincularUsuario);
					perfis.findByUsuarioIdAndAtivoTrue(usuarioId).forEach(PerfilAcesso::desativar);
				});
				return null;
			});
		}
		transacao.executeWithoutResult(s -> {
			usuarios.findById(usuarioId).orElseThrow().excluir(senhas.encode(Cofre.tokenAleatorio()), Instant.now(relogio));
			eventos.publishEvent(new ContaExcluida(usuarioId, List.copyOf(condominios)));
		});
	}

}
