package br.com.condomix.financeiro.web;

import br.com.condomix.acesso.Permissoes;
import br.com.condomix.financeiro.aplicacao.ConfiguracaoDeCobrancas;
import br.com.condomix.financeiro.dominio.ConfiguracaoDaCobranca;
import br.com.condomix.financeiro.dominio.GrupoDeUnidades;
import br.com.condomix.financeiro.dominio.TipoDeRateio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Tag(name = "Financeiro: configuração")
@RestController
@RequestMapping("/api/financeiro")
class ConfiguracaoFinanceiraController {

	private final ConfiguracaoDeCobrancas configuracao;

	ConfiguracaoFinanceiraController(ConfiguracaoDeCobrancas configuracao) {
		this.configuracao = configuracao;
	}

	record PedidoConfiguracao(@NotNull TipoDeRateio tipoRateio, @NotNull Integer diaVencimento,
			@NotNull BigDecimal multaPercentual, @NotNull BigDecimal jurosMensalPercentual,
			@NotNull BigDecimal fundoReservaPercentual) {
	}

	record ConfiguracaoResposta(TipoDeRateio tipoRateio, String tipoRateioRotulo, int diaVencimento,
			BigDecimal multaPercentual, BigDecimal jurosMensalPercentual, BigDecimal fundoReservaPercentual) {

		static ConfiguracaoResposta de(ConfiguracaoDaCobranca c) {
			return new ConfiguracaoResposta(c.getTipoRateio(), c.getTipoRateio().rotulo(), c.getDiaVencimento(),
					c.getMultaPercentual(), c.getJurosMensalPercentual(), c.getFundoReservaPercentual());
		}

	}

	record PedidoGrupo(@NotBlank String nome, @NotNull BigDecimal valorMensal) {
	}

	record PedidoUnidadesDoGrupo(@NotNull List<UUID> unidades) {
	}

	record GrupoResposta(UUID id, String nome, BigDecimal valorMensal, List<UUID> unidades) {

		static GrupoResposta de(GrupoDeUnidades g, List<UUID> unidades) {
			return new GrupoResposta(g.getId(), g.getNome(), g.getValorMensal(), unidades);
		}

	}

	@Operation(summary = "Como a taxa é cobrada", description = "Sem configuração salva, mostra a padrão: fração ideal, dia 10, multa 2%, juros 1% ao mês.")
	@PreAuthorize(Permissoes.CONSULTA_DO_CADASTRO)
	@GetMapping("/configuracao")
	ConfiguracaoResposta obter() {
		return ConfiguracaoResposta.de(configuracao.atual());
	}

	@Operation(summary = "Definir como a taxa é cobrada",
			description = "tipoRateio: IGUALITARIO, FRACAO_IDEAL ou VALOR_POR_GRUPO. Multa até 2%. Vencimento entre os dias 1 e 28.")
	@PreAuthorize(Permissoes.GESTAO)
	@PutMapping("/configuracao")
	ConfiguracaoResposta salvar(@Valid @RequestBody PedidoConfiguracao p) {
		return ConfiguracaoResposta.de(configuracao.salvar(p.tipoRateio(), p.diaVencimento(), p.multaPercentual(),
				p.jurosMensalPercentual(), p.fundoReservaPercentual()));
	}

	@Operation(summary = "Grupos do rateio por grupo, com valores e unidades")
	@PreAuthorize(Permissoes.CONSULTA_DO_CADASTRO)
	@GetMapping("/grupos")
	List<GrupoResposta> grupos() {
		return configuracao.grupos().stream().map(g -> GrupoResposta.de(g.grupo(), g.unidades())).toList();
	}

	@Operation(summary = "Criar grupo (ex.: Apartamento tipo, Cobertura)")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping("/grupos")
	@ResponseStatus(HttpStatus.CREATED)
	GrupoResposta criarGrupo(@Valid @RequestBody PedidoGrupo p) {
		return GrupoResposta.de(configuracao.criarGrupo(p.nome(), p.valorMensal()), List.of());
	}

	@Operation(summary = "Alterar nome ou valor de um grupo")
	@PreAuthorize(Permissoes.GESTAO)
	@PutMapping("/grupos/{id}")
	GrupoResposta alterarGrupo(@PathVariable UUID id, @Valid @RequestBody PedidoGrupo p) {
		configuracao.alterarGrupo(id, p.nome(), p.valorMensal());
		return grupos().stream().filter(g -> g.id().equals(id)).findFirst().orElseThrow();
	}

	@Operation(summary = "Definir as unidades de um grupo (substitui as anteriores)")
	@PreAuthorize(Permissoes.GESTAO)
	@PutMapping("/grupos/{id}/unidades")
	GrupoResposta definirUnidades(@PathVariable UUID id, @Valid @RequestBody PedidoUnidadesDoGrupo p) {
		var g = configuracao.definirUnidades(id, p.unidades());
		return GrupoResposta.de(g.grupo(), g.unidades());
	}

	@Operation(summary = "Remover grupo")
	@PreAuthorize(Permissoes.GESTAO)
	@DeleteMapping("/grupos/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removerGrupo(@PathVariable UUID id) {
		configuracao.removerGrupo(id);
	}

}
