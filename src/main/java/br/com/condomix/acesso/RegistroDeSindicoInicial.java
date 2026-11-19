package br.com.condomix.acesso;

import br.com.condomix.acesso.convite.Convite;
import br.com.condomix.acesso.convite.EmissaoDeConvites;
import br.com.condomix.acesso.mandato.Mandato;
import br.com.condomix.acesso.mandato.RegistroDeMandatos;
import br.com.condomix.acesso.persistencia.ConviteRepository;
import br.com.condomix.acesso.persistencia.MandatoRepository;
import br.com.condomix.acesso.persistencia.PessoaRepository;
import br.com.condomix.compartilhado.DocumentoBrasileiro;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.compartilhado.TenantAtual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/** Cada operação roda numa só transação, dentro do condomínio do cliente. */
@Service
class RegistroDeSindicoInicial implements IngressoDeSindico {

	private final PessoaRepository pessoas;

	private final MandatoRepository mandatosGravados;

	private final ConviteRepository convitesGravados;

	private final RegistroDeMandatos mandatos;

	private final EmissaoDeConvites convites;

	private final TransactionTemplate transacao;

	private final Clock relogio;

	RegistroDeSindicoInicial(PessoaRepository pessoas, MandatoRepository mandatosGravados,
			ConviteRepository convitesGravados, RegistroDeMandatos mandatos, EmissaoDeConvites convites,
			TransactionTemplate transacao, Clock relogio) {
		this.pessoas = pessoas;
		this.mandatosGravados = mandatosGravados;
		this.convitesGravados = convitesGravados;
		this.mandatos = mandatos;
		this.convites = convites;
		this.transacao = transacao;
		this.relogio = relogio;
	}

	@Override
	public SindicoRegistrado registrar(UUID condominioId, NovoSindico sindico, UUID registradoPor) {
		return TenantAtual.executarComo(condominioId, () -> transacao.execute(status -> {
			Pessoa pessoa = pessoaDoSindico(condominioId, sindico);
			mandatos.registrar(new RegistroDeMandatos.NovoMandato(pessoa.getId(), Cargo.SINDICO,
					sindico.inicioMandato(), sindico.fimMandato(), null, null, null), registradoPor);
			ConviteGerado convite = pessoa.getUsuarioId() != null ? null
					: convites.convidar(pessoa.getId(), sindico.email(), null, registradoPor);
			return new SindicoRegistrado(atual().orElseThrow(), convite);
		}));
	}

	@Override
	public Optional<SindicoAtual> sindicoAtual(UUID condominioId) {
		return TenantAtual.executarComo(condominioId, () -> transacao.execute(status -> atual()));
	}

	@Override
	public ConviteGerado reconvidar(UUID condominioId, String email, UUID registradoPor) {
		return TenantAtual.executarComo(condominioId, () -> transacao.execute(status -> {
			Pessoa pessoa = mandatoAtual()
					.orElseThrow(() -> RegraDeNegocioException.conflito("Este condomínio não tem síndico registrado"))
					.getPessoa();
			if (pessoa.getUsuarioId() != null) {
				throw RegraDeNegocioException.conflito(pessoa.getNome() + " já criou o login no Condomix");
			}
			String destino = email == null || email.isBlank() ? pessoa.getEmail() : email.strip();
			if (destino == null) {
				throw RegraDeNegocioException.invalido("Informe o e-mail do síndico");
			}
			if (!destino.equalsIgnoreCase(String.valueOf(pessoa.getEmail()))) {
				try {
					pessoa.alterarContato(destino, pessoa.getTelefone());
				}
				catch (IllegalArgumentException e) {
					throw RegraDeNegocioException.invalido(e.getMessage());
				}
			}
			return convites.convidar(pessoa.getId(), destino, null, registradoPor);
		}));
	}

	/** Se já existe uma pessoa com o mesmo CPF/CNPJ no condomínio (ex.: um morador eleito), usa ela. */
	private Pessoa pessoaDoSindico(UUID condominioId, NovoSindico sindico) {
		String documento;
		try {
			documento = DocumentoBrasileiro.normalizar(sindico.documento());
		}
		catch (IllegalArgumentException e) {
			throw RegraDeNegocioException.invalido(e.getMessage());
		}
		if (documento != null) {
			Optional<Pessoa> existente = pessoas.findByDocumento(documento);
			if (existente.isPresent()) {
				return existente.get();
			}
		}
		Pessoa pessoa = new Pessoa(condominioId, sindico.nome());
		try {
			pessoa.definirDocumento(sindico.documento());
			pessoa.alterarContato(sindico.email(), null);
		}
		catch (IllegalArgumentException e) {
			throw RegraDeNegocioException.invalido(e.getMessage());
		}
		return pessoas.save(pessoa);
	}

	private Optional<SindicoAtual> atual() {
		Instant agora = Instant.now(relogio);
		return mandatoAtual().map(m -> {
			Pessoa p = m.getPessoa();
			Instant convitePendenteAte = convitesGravados.pendentesDaPessoa(p.getId()).stream()
					.filter(c -> c.podeSerUsadoEm(agora))
					.map(Convite::getExpiraEm)
					.max(Comparator.naturalOrder())
					.orElse(null);
			return new SindicoAtual(p.getNome(), p.getEmail(), m.getInicio(), m.getFimPrevisto(), p.getUsuarioId() != null,
					convitePendenteAte);
		});
	}

	/** O mandato em aberto que já começou; se nenhum começou, o próximo. */
	private Optional<Mandato> mandatoAtual() {
		LocalDate hoje = LocalDate.now(relogio);
		var emAberto = mandatosGravados.emAberto(Cargo.SINDICO);
		return emAberto.stream().filter(m -> !m.getInicio().isAfter(hoje)).max(Comparator.comparing(Mandato::getInicio))
				.or(() -> emAberto.stream().min(Comparator.comparing(Mandato::getInicio)));
	}

}
