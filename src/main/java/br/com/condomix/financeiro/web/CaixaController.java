package br.com.condomix.financeiro.web;

import br.com.condomix.acesso.Permissoes;
import br.com.condomix.financeiro.aplicacao.Balancete;
import br.com.condomix.financeiro.aplicacao.ConsultaDoCaixa;
import br.com.condomix.financeiro.aplicacao.MovimentacaoDeCaixa;
import br.com.condomix.financeiro.aplicacao.PlanoDeContas;
import br.com.condomix.financeiro.dominio.CategoriaFinanceira;
import br.com.condomix.financeiro.dominio.CategoriaPadrao;
import br.com.condomix.financeiro.dominio.ContaFinanceira;
import br.com.condomix.financeiro.dominio.Lancamento;
import br.com.condomix.financeiro.dominio.Natureza;
import br.com.condomix.financeiro.dominio.OrigemDoLancamento;
import br.com.condomix.financeiro.dominio.TipoDeConta;
import br.com.condomix.financeiro.dominio.TipoDeLancamento;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Tag(name = "Financeiro: caixa e balancete")
@RestController
@RequestMapping("/api/financeiro")
class CaixaController {

	private final PlanoDeContas plano;

	private final MovimentacaoDeCaixa movimentacao;

	private final ConsultaDoCaixa consulta;

	private final Balancete balancete;

	CaixaController(PlanoDeContas plano, MovimentacaoDeCaixa movimentacao, ConsultaDoCaixa consulta, Balancete balancete) {
		this.plano = plano;
		this.movimentacao = movimentacao;
		this.consulta = consulta;
		this.balancete = balancete;
	}

	record PedidoConta(@NotBlank String nome, @NotNull TipoDeConta tipo, String banco, String agencia, String numero,
			BigDecimal saldoInicial, LocalDate dataSaldoInicial) {

		PlanoDeContas.DadosDaConta dados() {
			return new PlanoDeContas.DadosDaConta(nome, tipo, banco, agencia, numero);
		}

	}

	record ContaResposta(UUID id, String nome, TipoDeConta tipo, String banco, String agencia, String numero,
			BigDecimal saldoInicial, LocalDate dataSaldoInicial, boolean recebimentoPadrao, boolean ativa, BigDecimal saldoAtual) {

		static ContaResposta de(ContaFinanceira c, BigDecimal saldo) {
			return new ContaResposta(c.getId(), c.getNome(), c.getTipo(), c.getBanco(), c.getAgencia(), c.getNumero(),
					c.getSaldoInicial(), c.getDataSaldoInicial(), c.isRecebimentoPadrao(), c.isAtiva(), saldo);
		}

	}

	record PedidoCategoria(@NotBlank String nome, @NotNull Natureza natureza) {
	}

	record CategoriaResposta(UUID id, String nome, Natureza natureza, CategoriaPadrao codigo, boolean ativa) {

		static CategoriaResposta de(CategoriaFinanceira c) {
			return new CategoriaResposta(c.getId(), c.getNome(), c.getNatureza(), c.getCodigo(), c.isAtiva());
		}

	}

	record PedidoLancamento(@NotNull TipoDeLancamento tipo, @NotNull UUID contaId, @NotNull UUID categoriaId,
			LocalDate data, @NotBlank String descricao, @NotNull BigDecimal valor, String favorecido) {
	}

	record PedidoTransferencia(@NotNull UUID contaOrigemId, @NotNull UUID contaDestinoId, LocalDate data,
			@NotNull BigDecimal valor, String descricao) {
	}

	record PedidoCancelamento(@NotBlank String motivo) {
	}

	record LancamentoResposta(UUID id, LocalDate data, TipoDeLancamento tipo, OrigemDoLancamento origem, UUID contaId,
			String conta, String categoria, String descricao, BigDecimal valor, String favorecido, UUID cobrancaId,
			boolean cancelado, String motivoCancelamento) {

		static LancamentoResposta de(Lancamento l) {
			return new LancamentoResposta(l.getId(), l.getData(), l.getTipo(), l.getOrigem(), l.getConta().getId(),
					l.getConta().getNome(), l.getCategoria() == null ? null : l.getCategoria().getNome(), l.getDescricao(),
					l.getValor(), l.getFavorecido(), l.getCobrancaId(), l.isCancelado(), l.getMotivoCancelamento());
		}

	}

	// ----- contas -----

	@Operation(summary = "Contas do condomínio, com o saldo de hoje")
	@PreAuthorize(Permissoes.CONSULTA_DO_CADASTRO)
	@GetMapping("/contas")
	List<ContaResposta> contas() {
		return consulta.saldos(null).stream().map(s -> ContaResposta.de(s.conta(), s.saldo())).toList();
	}

	@Operation(summary = "Cadastrar conta",
			description = "tipo: CONTA_CORRENTE, POUPANCA, APLICACAO, CAIXA_FISICO. saldoInicial na dataSaldoInicial "
					+ "(lançamentos só a partir dela). A primeira conta vira a de recebimento das cobranças.")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping("/contas")
	@ResponseStatus(HttpStatus.CREATED)
	ContaResposta criarConta(@Valid @RequestBody PedidoConta p) {
		var c = plano.criarConta(p.dados(), p.saldoInicial(), p.dataSaldoInicial());
		return ContaResposta.de(c, c.getSaldoInicial());
	}

	@Operation(summary = "Corrigir nome, tipo e dados bancários de uma conta")
	@PreAuthorize(Permissoes.GESTAO)
	@PutMapping("/contas/{id}")
	ContaResposta alterarConta(@PathVariable UUID id, @Valid @RequestBody PedidoConta p) {
		plano.alterarConta(id, p.dados());
		return contas().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
	}

	@Operation(summary = "Usar esta conta para os pagamentos das cobranças")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping("/contas/{id}/recebimento-padrao")
	ContaResposta definirRecebimentoPadrao(@PathVariable UUID id) {
		plano.definirRecebimentoPadrao(id);
		return contas().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
	}

	@Operation(summary = "Desativar conta (o histórico continua no balancete)")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping("/contas/{id}/desativacao")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void desativarConta(@PathVariable UUID id) {
		plano.desativarConta(id);
	}

	// ----- plano de contas -----

	@Operation(summary = "Plano de contas: categorias de receita e despesa", description = "O plano padrão é criado no primeiro acesso.")
	@PreAuthorize(Permissoes.CONSULTA_DO_CADASTRO)
	@GetMapping("/categorias")
	List<CategoriaResposta> categorias() {
		return plano.categorias().stream().map(CategoriaResposta::de).toList();
	}

	@Operation(summary = "Criar categoria")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping("/categorias")
	@ResponseStatus(HttpStatus.CREATED)
	CategoriaResposta criarCategoria(@Valid @RequestBody PedidoCategoria p) {
		return CategoriaResposta.de(plano.criarCategoria(p.nome(), p.natureza()));
	}

	@Operation(summary = "Desativar categoria criada pelo condomínio")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping("/categorias/{id}/desativacao")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void desativarCategoria(@PathVariable UUID id) {
		plano.desativarCategoria(id);
	}

	// ----- lançamentos -----

	@Operation(summary = "Lançar despesa ou receita avulsa",
			description = "tipo: DESPESA ou RECEITA; a categoria tem que ser da mesma natureza. data vazia = hoje. "
					+ "Os pagamentos das cobranças entram sozinhos, não precisam ser lançados.")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping("/lancamentos")
	@ResponseStatus(HttpStatus.CREATED)
	LancamentoResposta lancar(@Valid @RequestBody PedidoLancamento p, @AuthenticationPrincipal Jwt jwt) {
		return LancamentoResposta.de(movimentacao.lancar(new MovimentacaoDeCaixa.Movimento(p.tipo(), p.contaId(),
				p.categoriaId(), p.data(), p.descricao(), p.valor(), p.favorecido()), UUID.fromString(jwt.getSubject())));
	}

	@Operation(summary = "Transferir entre contas (ex.: aplicar no fundo de reserva)", description = "Não conta como receita nem despesa.")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping("/transferencias")
	@ResponseStatus(HttpStatus.CREATED)
	List<LancamentoResposta> transferir(@Valid @RequestBody PedidoTransferencia p, @AuthenticationPrincipal Jwt jwt) {
		return movimentacao.transferir(p.contaOrigemId(), p.contaDestinoId(), p.data(), p.valor(), p.descricao(),
				UUID.fromString(jwt.getSubject())).stream().map(LancamentoResposta::de).toList();
	}

	@Operation(summary = "Cancelar um lançamento manual ou uma transferência")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping("/lancamentos/{id}/cancelamento")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void cancelar(@PathVariable UUID id, @Valid @RequestBody PedidoCancelamento p) {
		movimentacao.cancelar(id, p.motivo());
	}

	@Operation(summary = "Extrato: lançamentos do período (inclusive cancelados)", description = "de/ate: datas; contaId opcional.")
	@PreAuthorize(Permissoes.CONSULTA_DO_CADASTRO)
	@GetMapping("/lancamentos")
	List<LancamentoResposta> extrato(@RequestParam LocalDate de, @RequestParam LocalDate ate,
			@RequestParam(required = false) UUID contaId) {
		return consulta.extrato(de, ate, contaId).stream().map(LancamentoResposta::de).toList();
	}

	// ----- balancete -----

	@Operation(summary = "Balancete do mês (prestação de contas)",
			description = "Aberto a todos do condomínio: saldos por conta, receitas e despesas por categoria, resultado e inadimplência.")
	@GetMapping("/balancete")
	Balancete.DoMes balancete(@RequestParam YearMonth competencia) {
		return balancete.doMes(competencia);
	}

}
