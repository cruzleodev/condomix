package br.com.condomix.acesso.unidade;

import br.com.condomix.acesso.Unidade;
import br.com.condomix.acesso.VinculoUnidade;
import br.com.condomix.acesso.persistencia.UnidadeRepository;
import br.com.condomix.acesso.persistencia.VinculoUnidadeRepository;
import br.com.condomix.compartilhado.NaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/** Caso de uso: listar as unidades com quem está ligado a cada uma, e o resumo das frações ideais. */
@Service
@Transactional(readOnly = true)
public class ConsultaDeUnidades {

	private final UnidadeRepository unidades;

	private final VinculoUnidadeRepository vinculos;

	private final Clock relogio;

	public ConsultaDeUnidades(UnidadeRepository unidades, VinculoUnidadeRepository vinculos, Clock relogio) {
		this.unidades = unidades;
		this.vinculos = vinculos;
		this.relogio = relogio;
	}

	public record UnidadeComOcupantes(Unidade unidade, List<VinculoUnidade> vinculos) {
	}

	/**
	 * @param somaDasFracoes soma das frações ideais das unidades ativas; o certo é dar 1 (100%)
	 * @param fracoesConferem se a soma é exatamente 1 e todas as unidades ativas têm fração
	 */
	public record Resumo(long total, long ativas, long semFracaoIdeal, BigDecimal somaDasFracoes,
			boolean fracoesConferem, List<String> blocos) {
	}

	public List<UnidadeComOcupantes> listar(String bloco, boolean incluirInativas) {
		Map<UUID, List<VinculoUnidade>> porUnidade = vinculosVigentesPorUnidade();
		return unidades.todasEmOrdem().stream()
				.filter(u -> incluirInativas || u.isAtivo())
				.filter(u -> bloco == null || bloco.isBlank() || bloco.strip().equalsIgnoreCase(u.getBloco()))
				.map(u -> new UnidadeComOcupantes(u, porUnidade.getOrDefault(u.getId(), List.of())))
				.toList();
	}

	public UnidadeComOcupantes obter(UUID id) {
		Unidade unidade = unidades.findById(id).orElseThrow(() -> new NaoEncontradoException("Unidade não encontrada"));
		return new UnidadeComOcupantes(unidade, vinculosVigentesPorUnidade().getOrDefault(id, List.of()));
	}

	public Resumo resumo() {
		List<Unidade> todas = unidades.findAll();
		List<Unidade> ativas = todas.stream().filter(Unidade::isAtivo).toList();
		BigDecimal soma = ativas.stream().map(Unidade::getFracaoIdeal).filter(Objects::nonNull)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		long semFracao = ativas.stream().filter(u -> u.getFracaoIdeal() == null).count();
		List<String> blocos = ativas.stream().map(Unidade::getBloco).filter(Objects::nonNull).distinct().sorted().toList();
		boolean conferem = !ativas.isEmpty() && semFracao == 0 && soma.compareTo(BigDecimal.ONE) == 0;
		return new Resumo(todas.size(), ativas.size(), semFracao, soma, conferem, blocos);
	}

	private Map<UUID, List<VinculoUnidade>> vinculosVigentesPorUnidade() {
		return vinculos.vigentes(LocalDate.now(relogio)).stream()
				.collect(Collectors.groupingBy(v -> v.getUnidade().getId()));
	}

}
