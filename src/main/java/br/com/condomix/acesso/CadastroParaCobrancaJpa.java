package br.com.condomix.acesso;

import br.com.condomix.acesso.persistencia.MandatoRepository;
import br.com.condomix.acesso.persistencia.PessoaRepository;
import br.com.condomix.acesso.persistencia.UnidadeRepository;
import br.com.condomix.acesso.persistencia.VinculoUnidadeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
class CadastroParaCobrancaJpa implements CadastroParaCobranca {

	/** Quem responde pela cobrança, em ordem de preferência. */
	private static final List<TipoVinculo> ORDEM_DO_RESPONSAVEL =
			List.of(TipoVinculo.PROPRIETARIO, TipoVinculo.INQUILINO, TipoVinculo.MORADOR);

	private static final Set<TipoVinculo> PAGADORES = EnumSet.of(TipoVinculo.PROPRIETARIO, TipoVinculo.INQUILINO);

	private final UnidadeRepository unidades;

	private final VinculoUnidadeRepository vinculos;

	private final MandatoRepository mandatos;

	private final PessoaRepository pessoas;

	CadastroParaCobrancaJpa(UnidadeRepository unidades, VinculoUnidadeRepository vinculos, MandatoRepository mandatos,
			PessoaRepository pessoas) {
		this.unidades = unidades;
		this.vinculos = vinculos;
		this.mandatos = mandatos;
		this.pessoas = pessoas;
	}

	@Override
	public Optional<DadosDoPagador> pagador(UUID pessoaId) {
		return pessoas.findById(pessoaId)
				.map(p -> new DadosDoPagador(p.getId(), p.getNome(), p.getDocumento(), p.getEmail()));
	}

	@Override
	public List<UnidadeCobravel> unidadesAtivas(LocalDate data) {
		Map<UUID, List<VinculoUnidade>> porUnidade = vinculos.vigentes(data).stream()
				.collect(Collectors.groupingBy(v -> v.getUnidade().getId()));
		return unidades.todasEmOrdem().stream()
				.filter(Unidade::isAtivo)
				.map(u -> new UnidadeCobravel(u.getId(), u.descricao(), u.getFracaoIdeal(),
						responsavel(porUnidade.getOrDefault(u.getId(), List.of()))))
				.toList();
	}

	@Override
	public List<BeneficioDeMandato> beneficiosNoPeriodo(LocalDate inicio, LocalDate fim) {
		return mandatos.comBeneficioNoPeriodo(inicio, fim).stream()
				.map(m -> new BeneficioDeMandato(m.getBeneficio().orElseThrow().unidadeId(),
						m.getBeneficio().orElseThrow().percentual(), m.getInicio(), m.getEncerradoEm(), m.getCargo()))
				.toList();
	}

	@Override
	public Set<UUID> unidadesDoPagador(UUID usuarioId, LocalDate data) {
		return vinculos.vigentesDoUsuario(usuarioId, data).stream()
				.filter(v -> PAGADORES.contains(v.getTipo()))
				.map(v -> v.getUnidade().getId())
				.collect(Collectors.toSet());
	}

	private static Responsavel responsavel(List<VinculoUnidade> vinculosDaUnidade) {
		return vinculosDaUnidade.stream()
				.filter(v -> ORDEM_DO_RESPONSAVEL.contains(v.getTipo()))
				.min(Comparator.comparing((VinculoUnidade v) -> ORDEM_DO_RESPONSAVEL.indexOf(v.getTipo()))
						.thenComparing(VinculoUnidade::getInicio))
				.map(v -> new Responsavel(v.getPessoa().getId(), v.getPessoa().getNome(), v.getPessoa().getDocumento()))
				.orElse(null);
	}

}
