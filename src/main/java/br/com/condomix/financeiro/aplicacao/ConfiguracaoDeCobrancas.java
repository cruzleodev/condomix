package br.com.condomix.financeiro.aplicacao;

import br.com.condomix.acesso.CadastroParaCobranca;
import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.financeiro.dominio.ConfiguracaoDaCobranca;
import br.com.condomix.financeiro.dominio.GrupoDeUnidades;
import br.com.condomix.financeiro.dominio.MembroDeGrupo;
import br.com.condomix.financeiro.dominio.TipoDeRateio;
import br.com.condomix.financeiro.persistencia.ConfiguracaoDaCobrancaRepository;
import br.com.condomix.financeiro.persistencia.GrupoDeUnidadesRepository;
import br.com.condomix.financeiro.persistencia.MembroDeGrupoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Caso de uso: o síndico define como a taxa é cobrada e, no rateio por grupo, os grupos e seus valores. */
@Service
@Transactional
public class ConfiguracaoDeCobrancas {

	private final ConfiguracaoDaCobrancaRepository configuracoes;

	private final GrupoDeUnidadesRepository grupos;

	private final MembroDeGrupoRepository membros;

	private final CadastroParaCobranca cadastro;

	private final Clock relogio;

	public ConfiguracaoDeCobrancas(ConfiguracaoDaCobrancaRepository configuracoes, GrupoDeUnidadesRepository grupos,
			MembroDeGrupoRepository membros, CadastroParaCobranca cadastro, Clock relogio) {
		this.configuracoes = configuracoes;
		this.grupos = grupos;
		this.membros = membros;
		this.cadastro = cadastro;
		this.relogio = relogio;
	}

	/** A configuração do condomínio; sem nenhuma salva, a padrão (ainda não gravada). */
	@Transactional(readOnly = true)
	public ConfiguracaoDaCobranca atual() {
		return configuracoes.findFirstBy().orElseGet(() -> ConfiguracaoDaCobranca.padrao(TenantAtual.obrigatorio()));
	}

	public ConfiguracaoDaCobranca salvar(TipoDeRateio tipo, int diaVencimento, BigDecimal multa, BigDecimal juros,
			BigDecimal fundoReserva) {
		ConfiguracaoDaCobranca configuracao = configuracoes.findFirstBy()
				.orElseGet(() -> ConfiguracaoDaCobranca.padrao(TenantAtual.obrigatorio()));
		Regras.aplicar(() -> configuracao.alterar(tipo, diaVencimento, multa, juros, fundoReserva));
		return configuracoes.save(configuracao);
	}

	public record GrupoComUnidades(GrupoDeUnidades grupo, List<UUID> unidades) {
	}

	@Transactional(readOnly = true)
	public List<GrupoComUnidades> grupos() {
		Map<UUID, List<UUID>> porGrupo = membros.findAll().stream()
				.collect(Collectors.groupingBy(MembroDeGrupo::getGrupoId,
						Collectors.mapping(MembroDeGrupo::getUnidadeId, Collectors.toList())));
		return grupos.findAll().stream()
				.sorted((a, b) -> a.getNome().compareToIgnoreCase(b.getNome()))
				.map(g -> new GrupoComUnidades(g, porGrupo.getOrDefault(g.getId(), List.of())))
				.toList();
	}

	public GrupoDeUnidades criarGrupo(String nome, BigDecimal valorMensal) {
		GrupoDeUnidades grupo = Regras.aplicar(() -> new GrupoDeUnidades(TenantAtual.obrigatorio(), nome, valorMensal));
		if (grupos.findAll().stream().anyMatch(g -> g.getNome().equalsIgnoreCase(grupo.getNome()))) {
			throw RegraDeNegocioException.conflito("Já existe um grupo chamado " + grupo.getNome());
		}
		return grupos.save(grupo);
	}

	public GrupoDeUnidades alterarGrupo(UUID id, String nome, BigDecimal valorMensal) {
		GrupoDeUnidades grupo = buscarGrupo(id);
		Regras.aplicar(() -> grupo.alterar(nome, valorMensal));
		return grupo;
	}

	public void removerGrupo(UUID id) {
		GrupoDeUnidades grupo = buscarGrupo(id);
		membros.deleteByGrupoId(id);
		grupos.delete(grupo);
	}

	/** Define as unidades do grupo (substitui as anteriores). Uma unidade que estava em outro grupo muda para este. */
	public GrupoComUnidades definirUnidades(UUID grupoId, List<UUID> unidades) {
		buscarGrupo(grupoId);
		Set<UUID> ativas = cadastro.unidadesAtivas(LocalDate.now(relogio)).stream()
				.map(CadastroParaCobranca.UnidadeCobravel::id).collect(Collectors.toSet());
		Set<UUID> pedidas = new HashSet<>(unidades);
		if (!ativas.containsAll(pedidas)) {
			throw RegraDeNegocioException.invalido("Há unidades inexistentes ou desativadas na lista");
		}
		membros.deleteByGrupoId(grupoId);
		membros.deleteByUnidadeIdIn(pedidas);
		membros.flush();
		UUID tenant = TenantAtual.obrigatorio();
		pedidas.forEach(u -> membros.save(new MembroDeGrupo(tenant, u, grupoId)));
		return new GrupoComUnidades(buscarGrupo(grupoId), List.copyOf(pedidas));
	}

	private GrupoDeUnidades buscarGrupo(UUID id) {
		return grupos.findById(id).orElseThrow(() -> new NaoEncontradoException("Grupo não encontrado"));
	}

}
