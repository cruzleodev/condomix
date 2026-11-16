package br.com.condomix.financeiro.rateio;

import br.com.condomix.acesso.CadastroParaCobranca.UnidadeCobravel;
import br.com.condomix.financeiro.dominio.GrupoDeUnidades;
import br.com.condomix.financeiro.dominio.MembroDeGrupo;
import br.com.condomix.financeiro.dominio.TipoDeRateio;
import br.com.condomix.financeiro.persistencia.GrupoDeUnidadesRepository;
import br.com.condomix.financeiro.persistencia.MembroDeGrupoRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Cada unidade paga o valor do seu grupo. O "total" não se aplica: é a soma dos grupos. */
@Component
class ValorPorGrupo implements CalculoDoRateio {

	private final GrupoDeUnidadesRepository grupos;

	private final MembroDeGrupoRepository membros;

	ValorPorGrupo(GrupoDeUnidadesRepository grupos, MembroDeGrupoRepository membros) {
		this.grupos = grupos;
		this.membros = membros;
	}

	@Override
	public TipoDeRateio tipo() {
		return TipoDeRateio.VALOR_POR_GRUPO;
	}

	@Override
	public Rateio calcular(BigDecimal valorTotal, List<UnidadeCobravel> unidades) {
		Map<UUID, GrupoDeUnidades> grupoPorId = grupos.findAll().stream()
				.collect(Collectors.toMap(GrupoDeUnidades::getId, Function.identity()));
		Map<UUID, UUID> grupoDaUnidade = membros.findAll().stream()
				.collect(Collectors.toMap(MembroDeGrupo::getUnidadeId, MembroDeGrupo::getGrupoId));
		List<String> semGrupo = unidades.stream().filter(u -> !grupoDaUnidade.containsKey(u.id()))
				.map(UnidadeCobravel::descricao).toList();
		if (!semGrupo.isEmpty()) {
			throw new IllegalArgumentException("Coloque estas unidades em um grupo antes de gerar: " + String.join(", ", semGrupo));
		}
		Map<UUID, BigDecimal> valores = new LinkedHashMap<>();
		unidades.forEach(u -> valores.put(u.id(), grupoPorId.get(grupoDaUnidade.get(u.id())).getValorMensal()));
		List<String> avisos = valorTotal == null ? List.of()
				: List.of("No rateio por grupo o valor total informado é ignorado: cada grupo tem o seu valor.");
		return new Rateio(valores, avisos);
	}

}
