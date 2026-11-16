package br.com.condomix.financeiro.web;

import br.com.condomix.acesso.Permissoes;
import br.com.condomix.compartilhado.Pagina;
import br.com.condomix.financeiro.aplicacao.BaixaDeCobrancas;
import br.com.condomix.financeiro.aplicacao.ConsultaDeCobrancas;
import br.com.condomix.financeiro.aplicacao.EmissaoAvulsa;
import br.com.condomix.financeiro.aplicacao.ImportacaoDeCobrancas;
import br.com.condomix.financeiro.dominio.FormaDePagamento;
import br.com.condomix.financeiro.dominio.StatusCobranca;
import br.com.condomix.financeiro.dominio.TipoDeItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Tag(name = "Financeiro: cobranças")
@RestController
@RequestMapping("/api")
class CobrancaController {

	private final ConsultaDeCobrancas consulta;

	private final BaixaDeCobrancas baixa;

	private final EmissaoAvulsa avulsa;

	private final ImportacaoDeCobrancas importacao;

	CobrancaController(ConsultaDeCobrancas consulta, BaixaDeCobrancas baixa, EmissaoAvulsa avulsa,
			ImportacaoDeCobrancas importacao) {
		this.consulta = consulta;
		this.baixa = baixa;
		this.avulsa = avulsa;
		this.importacao = importacao;
	}

	record PedidoAvulsa(@NotNull UUID unidadeId, @NotNull TipoDeItem tipo, @NotBlank String descricao,
			@NotNull BigDecimal valor, @NotNull LocalDate vencimento) {
	}

	record PedidoPagamento(LocalDate data, BigDecimal valorPago, @NotNull FormaDePagamento forma, String observacao) {
	}

	record PedidoCancelamento(@NotBlank String motivo) {
	}

	@Operation(summary = "Cobranças do condomínio (filtros opcionais)", description = "competencia: \"2026-12\"; status: ABERTA, PAGA, CANCELADA.")
	@PreAuthorize(Permissoes.CONSULTA_DO_CADASTRO)
	@GetMapping("/cobrancas")
	Pagina<CobrancaResposta> listar(@RequestParam(required = false) YearMonth competencia,
			@RequestParam(required = false) StatusCobranca status, @RequestParam(required = false) UUID unidadeId,
			@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "50") int tamanho) {
		return Pagina.de(consulta.listar(competencia, status, unidadeId, pagina, tamanho), CobrancaResposta::de);
	}

	@Operation(summary = "Uma cobrança, com o valor atualizado para hoje")
	@PreAuthorize(Permissoes.CONSULTA_DO_CADASTRO)
	@GetMapping("/cobrancas/{id}")
	CobrancaResposta obter(@PathVariable UUID id) {
		return CobrancaResposta.de(consulta.obter(id));
	}

	@Operation(summary = "Inadimplência: unidades com cobranças vencidas, com multa e juros até hoje")
	@PreAuthorize(Permissoes.CONSULTA_DO_CADASTRO)
	@GetMapping("/cobrancas/inadimplencia")
	List<ConsultaDeCobrancas.Inadimplente> inadimplencia() {
		return consulta.inadimplencia();
	}

	@Operation(summary = "Cobrança avulsa para uma unidade",
			description = "tipo: MULTA_DISCIPLINAR, RESERVA_DE_ESPACO, CONSUMO, TAXA_EXTRA ou OUTRO.")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping("/cobrancas")
	@ResponseStatus(HttpStatus.CREATED)
	CobrancaResposta emitir(@Valid @RequestBody PedidoAvulsa p, @AuthenticationPrincipal Jwt jwt) {
		var c = avulsa.emitir(p.unidadeId(), p.tipo(), p.descricao(), p.valor(), p.vencimento(), UUID.fromString(jwt.getSubject()));
		return CobrancaResposta.de(consulta.obter(c.getId()));
	}

	@Operation(summary = "Registrar pagamento (baixa manual)",
			description = "Sem valorPago, usa o valor com multa e juros na data. data vazia = hoje.")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping("/cobrancas/{id}/pagamento")
	CobrancaResposta pagar(@PathVariable UUID id, @Valid @RequestBody PedidoPagamento p, @AuthenticationPrincipal Jwt jwt) {
		baixa.registrarPagamento(id, p.data(), p.valorPago(), p.forma(), p.observacao(), UUID.fromString(jwt.getSubject()));
		return obter(id);
	}

	@Operation(summary = "Desfazer um pagamento registrado por engano")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping("/cobrancas/{id}/estorno")
	CobrancaResposta estornar(@PathVariable UUID id) {
		baixa.estornarPagamento(id);
		return obter(id);
	}

	@Operation(summary = "Cancelar uma cobrança em aberto")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping("/cobrancas/{id}/cancelamento")
	CobrancaResposta cancelar(@PathVariable UUID id, @Valid @RequestBody PedidoCancelamento p) {
		baixa.cancelar(id, p.motivo());
		return obter(id);
	}

	@Operation(summary = "Importar cobranças da administradora (planilha CSV)",
			description = "Cabeçalho: unidade;competencia;vencimento;valor;situacao;data_pagamento;valor_pago;descricao. "
					+ "Tudo ou nada: com erro em qualquer linha, nada é importado e a resposta lista os erros.")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping(value = "/cobrancas/importacao", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	ImportacaoDeCobrancas.Resultado importar(@RequestPart("arquivo") MultipartFile arquivo, @AuthenticationPrincipal Jwt jwt)
			throws IOException {
		return importacao.importar(arquivo.getBytes(), UUID.fromString(jwt.getSubject()));
	}

	@Operation(summary = "Meus débitos: cobranças das minhas unidades (proprietário ou inquilino)",
			description = "Em aberto, com multa e juros até hoje, e as dos últimos 12 meses.")
	@GetMapping("/eu/cobrancas")
	List<CobrancaResposta> minhas(@AuthenticationPrincipal Jwt jwt) {
		return consulta.doUsuario(UUID.fromString(jwt.getSubject())).stream().map(CobrancaResposta::de).toList();
	}

}
