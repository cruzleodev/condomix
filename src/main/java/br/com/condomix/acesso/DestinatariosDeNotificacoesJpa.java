package br.com.condomix.acesso;

import br.com.condomix.acesso.mandato.Mandato;
import br.com.condomix.acesso.persistencia.MandatoRepository;
import br.com.condomix.acesso.persistencia.PerfilAcessoRepository;
import br.com.condomix.acesso.persistencia.PessoaRepository;
import br.com.condomix.acesso.persistencia.UsuarioRepository;
import br.com.condomix.acesso.persistencia.VinculoUnidadeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
class DestinatariosDeNotificacoesJpa implements DestinatariosDeNotificacoes {

	private static final Set<TipoVinculo> PAGADORES = EnumSet.of(TipoVinculo.PROPRIETARIO, TipoVinculo.INQUILINO);

	private static final Set<TipoVinculo> OCUPANTES = EnumSet.of(TipoVinculo.PROPRIETARIO, TipoVinculo.INQUILINO, TipoVinculo.MORADOR);

	private final VinculoUnidadeRepository vinculos;

	private final MandatoRepository mandatos;

	private final PerfilAcessoRepository perfis;

	private final UsuarioRepository usuarios;

	private final PessoaRepository pessoas;

	private final Clock relogio;

	DestinatariosDeNotificacoesJpa(VinculoUnidadeRepository vinculos, MandatoRepository mandatos, PerfilAcessoRepository perfis,
			UsuarioRepository usuarios, PessoaRepository pessoas, Clock relogio) {
		this.pessoas = pessoas;
		this.vinculos = vinculos;
		this.mandatos = mandatos;
		this.perfis = perfis;
		this.usuarios = usuarios;
		this.relogio = relogio;
	}

	@Override
	public List<Destinatario> pagadoresDaUnidade(UUID unidadeId) {
		return daUnidade(unidadeId, PAGADORES);
	}

	@Override
	public List<Destinatario> ocupantesDaUnidade(UUID unidadeId) {
		return daUnidade(unidadeId, OCUPANTES);
	}

	@Override
	public List<Destinatario> daGestao() {
		LocalDate hoje = LocalDate.now(relogio);
		Map<UUID, String> nomes = new LinkedHashMap<>();
		mandatos.comAcessoNaDataDeTodos(hoje).stream()
				.filter(m -> m.getCargo() == Cargo.SINDICO && m.getPessoa().getUsuarioId() != null)
				.forEach((Mandato m) -> nomes.putIfAbsent(m.getPessoa().getUsuarioId(), m.getPessoa().getNome()));
		perfis.ativosComUsuario().stream()
				.filter(p -> p.getPerfil() == Perfil.ADMINISTRADORA)
				.forEach(p -> nomes.putIfAbsent(p.getUsuario().getId(), p.getPessoa() == null ? null : p.getPessoa().getNome()));
		return comLoginAtivo(nomes);
	}

	@Override
	public List<Destinatario> doCondominio() {
		LocalDate hoje = LocalDate.now(relogio);
		Map<UUID, String> nomes = new LinkedHashMap<>();
		mandatos.comAcessoNaDataDeTodos(hoje).stream()
				.filter(m -> m.getPessoa().getUsuarioId() != null)
				.forEach((Mandato m) -> nomes.putIfAbsent(m.getPessoa().getUsuarioId(), m.getPessoa().getNome()));
		perfis.ativosComUsuario()
				.forEach(p -> nomes.putIfAbsent(p.getUsuario().getId(), p.getPessoa() == null ? null : p.getPessoa().getNome()));
		return comLoginAtivo(nomes);
	}

	@Override
	public Optional<Destinatario> usuario(UUID usuarioId) {
		String nome = pessoas.findFirstByUsuarioId(usuarioId).map(Pessoa::getNome).orElse("");
		return comLoginAtivo(Map.of(usuarioId, nome)).stream().findFirst();
	}

	private List<Destinatario> daUnidade(UUID unidadeId, Set<TipoVinculo> tipos) {
		LocalDate hoje = LocalDate.now(relogio);
		Map<UUID, String> nomes = new LinkedHashMap<>();
		vinculos.findByUnidadeId(unidadeId).stream()
				.filter(v -> v.isVigente(hoje) && tipos.contains(v.getTipo()) && v.getPessoa().getUsuarioId() != null)
				.forEach(v -> nomes.putIfAbsent(v.getPessoa().getUsuarioId(), v.getPessoa().getNome()));
		return comLoginAtivo(nomes);
	}

	/** Só logins ativos e não bloqueados. Sem nome da pessoa, usa o e-mail. */
	private List<Destinatario> comLoginAtivo(Map<UUID, String> nomes) {
		if (nomes.isEmpty()) {
			return List.of();
		}
		Map<UUID, Usuario> porId = usuarios.findAllById((Collection<UUID>) nomes.keySet()).stream()
				.filter(u -> u.isAtivo() && !u.isBloqueado())
				.collect(Collectors.toMap(Usuario::getId, Function.identity()));
		return nomes.entrySet().stream()
				.filter(e -> porId.containsKey(e.getKey()))
				.map(e -> {
					Usuario u = porId.get(e.getKey());
					String nome = e.getValue() == null || e.getValue().isBlank() ? u.getEmail() : e.getValue();
					return new Destinatario(u.getId(), u.getEmail(), nome);
				})
				.toList();
	}

}
