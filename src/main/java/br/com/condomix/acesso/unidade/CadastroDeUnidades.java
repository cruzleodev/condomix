package br.com.condomix.acesso.unidade;

import br.com.condomix.acesso.TipoUnidade;
import br.com.condomix.acesso.Unidade;
import br.com.condomix.acesso.persistencia.UnidadeRepository;
import br.com.condomix.acesso.persistencia.VinculoUnidadeRepository;
import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.compartilhado.RestricaoDoBanco;
import br.com.condomix.compartilhado.TenantAtual;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

/** Caso de uso: o síndico cadastra e corrige as unidades do condomínio. */
@Service
@Transactional
public class CadastroDeUnidades {

	public static final int MAXIMO_POR_LOTE = 500;

	private final UnidadeRepository unidades;

	private final VinculoUnidadeRepository vinculos;

	private final Clock relogio;

	public CadastroDeUnidades(UnidadeRepository unidades, VinculoUnidadeRepository vinculos, Clock relogio) {
		this.unidades = unidades;
		this.vinculos = vinculos;
		this.relogio = relogio;
	}

	public record DadosDaUnidade(String bloco, String identificacao, TipoUnidade tipo, BigDecimal fracaoIdeal) {
	}

	public Unidade criar(DadosDaUnidade dados) {
		Unidade unidade = regra(() -> {
			Unidade nova = new Unidade(TenantAtual.obrigatorio(), dados.bloco(), dados.identificacao(), dados.tipo());
			nova.definirFracaoIdeal(dados.fracaoIdeal());
			return nova;
		});
		return gravar(() -> unidades.saveAndFlush(unidade));
	}

	/** Várias unidades de uma vez (ex.: todas de um bloco). Tudo ou nada: se uma falhar, nenhuma é criada. */
	public List<Unidade> criarLote(String bloco, TipoUnidade tipo, List<String> identificacoes) {
		if (identificacoes.isEmpty() || identificacoes.size() > MAXIMO_POR_LOTE) {
			throw RegraDeNegocioException.invalido("Informe de 1 a " + MAXIMO_POR_LOTE + " unidades por lote");
		}
		var vistas = new HashSet<String>();
		for (String id : identificacoes) {
			if (id != null && !vistas.add(id.strip().toLowerCase(Locale.ROOT))) {
				throw RegraDeNegocioException.invalido("A unidade " + id.strip() + " aparece repetida no lote");
			}
		}
		UUID tenant = TenantAtual.obrigatorio();
		List<Unidade> novas = identificacoes.stream()
				.map(id -> regra(() -> new Unidade(tenant, bloco, id, tipo)))
				.toList();
		return gravar(() -> unidades.saveAllAndFlush(novas));
	}

	public Unidade alterar(UUID id, DadosDaUnidade dados) {
		Unidade unidade = buscar(id);
		regra(() -> {
			unidade.alterar(dados.bloco(), dados.identificacao(), dados.tipo());
			unidade.definirFracaoIdeal(dados.fracaoIdeal());
			return unidade;
		});
		return gravar(() -> unidades.saveAndFlush(unidade));
	}

	/** Unidade desativada some das listas e das cobranças. Só sem vínculos em aberto. */
	public void desativar(UUID id) {
		Unidade unidade = buscar(id);
		if (vinculos.vigentes(LocalDate.now(relogio)).stream().anyMatch(v -> v.getUnidade().getId().equals(id))) {
			throw RegraDeNegocioException.conflito("A unidade " + unidade.descricao()
					+ " ainda tem pessoas vinculadas: encerre os vínculos antes de desativar");
		}
		unidade.desativar();
	}

	public void reativar(UUID id) {
		buscar(id).reativar();
	}

	private Unidade buscar(UUID id) {
		return unidades.findById(id).orElseThrow(() -> new NaoEncontradoException("Unidade não encontrada"));
	}

	private static <T> T regra(Supplier<T> acao) {
		try {
			return acao.get();
		}
		catch (IllegalArgumentException | NullPointerException e) {
			throw RegraDeNegocioException.invalido(e.getMessage());
		}
	}

	private static <T> T gravar(Supplier<T> acao) {
		try {
			return acao.get();
		}
		catch (DataIntegrityViolationException e) {
			if (RestricaoDoBanco.violou(e, "unidade_tenant_id_bloco_identificacao_key")) {
				throw RegraDeNegocioException.conflito("Já existe uma unidade com este bloco e identificação");
			}
			throw e;
		}
	}

}
