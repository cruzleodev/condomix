package br.com.condomix.financeiro.web;

import br.com.condomix.acesso.Permissoes;
import br.com.condomix.financeiro.aplicacao.GeracaoMensal;
import br.com.condomix.financeiro.dominio.TipoDeRateio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Tag(name = "Financeiro: geração mensal")
@RestController
@RequestMapping("/api/financeiro/geracoes")
class GeracaoController {

	private final GeracaoMensal geracao;

	GeracaoController(GeracaoMensal geracao) {
		this.geracao = geracao;
	}

	record PedidoGeracao(@NotNull YearMonth competencia, BigDecimal valorOrdinariaTotal, LocalDate vencimento,
			List<@Valid PedidoTaxaExtra> taxasExtras) {

		GeracaoMensal.Pedido paraPedido() {
			return new GeracaoMensal.Pedido(competencia, valorOrdinariaTotal, vencimento,
					taxasExtras == null ? List.of() : taxasExtras.stream()
							.map(t -> new GeracaoMensal.TaxaExtra(t.descricao(), t.valorTotal(),
									t.rateio() == null ? TipoDeRateio.FRACAO_IDEAL : t.rateio()))
							.toList());
		}

	}

	record PedidoTaxaExtra(@NotBlank String descricao, @NotNull BigDecimal valorTotal, TipoDeRateio rateio) {
	}

	record PedidoCancelamento(String motivo) {
	}

	record GeracaoResumo(UUID id, YearMonth competencia, TipoDeRateio tipoRateio, BigDecimal valorOrdinariaTotal,
			Instant geradaEm, Instant canceladaEm) {
	}

	@Operation(summary = "Simular a taxa do mês (não grava nada)",
			description = "competencia: \"2026-12\". valorOrdinariaTotal: total da taxa ordinária do mês (não usado no rateio por grupo). "
					+ "taxasExtras: [{\"descricao\": \"Pintura da fachada\", \"valorTotal\": 30000, \"rateio\": \"FRACAO_IDEAL\"}].")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping("/simulacao")
	GeracaoMensal.Resultado simular(@Valid @RequestBody PedidoGeracao pedido) {
		return geracao.simular(pedido.paraPedido());
	}

	@Operation(summary = "Gerar a taxa do mês para todas as unidades", description = "Mesmos campos da simulação. Uma geração por mês.")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	GeracaoMensal.Resultado gerar(@Valid @RequestBody PedidoGeracao pedido, @AuthenticationPrincipal Jwt jwt) {
		return geracao.gerar(pedido.paraPedido(), UUID.fromString(jwt.getSubject()));
	}

	@Operation(summary = "Gerações feitas")
	@PreAuthorize(Permissoes.CONSULTA_DO_CADASTRO)
	@GetMapping
	List<GeracaoResumo> historico() {
		return geracao.historico().stream().map(g -> new GeracaoResumo(g.getId(), g.getCompetencia(), g.getTipoRateio(),
				g.getValorOrdinariaTotal(), g.getGeradaEm(), g.getCanceladaEm())).toList();
	}

	@Operation(summary = "Cancelar uma geração (e as cobranças dela)", description = "Não é possível se alguma cobrança já foi paga.")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping("/{id}/cancelamento")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void cancelar(@PathVariable UUID id, @RequestBody(required = false) PedidoCancelamento pedido) {
		geracao.cancelar(id, pedido == null ? null : pedido.motivo());
	}

}
