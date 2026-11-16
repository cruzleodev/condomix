package br.com.condomix.financeiro.aplicacao;

import br.com.condomix.financeiro.dominio.ContaFinanceira;
import br.com.condomix.financeiro.dominio.Lancamento;
import br.com.condomix.financeiro.dominio.Natureza;
import br.com.condomix.financeiro.persistencia.CobrancaRepository;
import br.com.condomix.financeiro.persistencia.ContaFinanceiraRepository;
import br.com.condomix.financeiro.persistencia.LancamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Caso de uso: o balancete do mês para a prestação de contas. Visível a todos do condomínio
 * (transparência): mostra totais por conta e por categoria, sem dados pessoais.
 */
@Service
@Transactional(readOnly = true)
public class Balancete {

	private final ContaFinanceiraRepository contas;

	private final LancamentoRepository lancamentos;

	private final CobrancaRepository cobrancas;

	public Balancete(ContaFinanceiraRepository contas, LancamentoRepository lancamentos, CobrancaRepository cobrancas) {
		this.contas = contas;
		this.lancamentos = lancamentos;
		this.cobrancas = cobrancas;
	}

	public record MovimentoDaConta(String conta, BigDecimal saldoAnterior, BigDecimal entradas, BigDecimal saidas,
			BigDecimal saldoFinal) {
	}

	public record TotalDaCategoria(String categoria, BigDecimal total) {
	}

	/**
	 * @param resultado        receitas menos despesas (transferências entre contas não entram)
	 * @param inadimplencia    soma das cobranças em aberto vencidas até o fim do mês (valor original)
	 */
	public record DoMes(YearMonth competencia, List<MovimentoDaConta> contas, List<TotalDaCategoria> receitas,
			BigDecimal totalReceitas, List<TotalDaCategoria> despesas, BigDecimal totalDespesas, BigDecimal resultado,
			BigDecimal saldoTotalFinal, BigDecimal inadimplencia) {
	}

	public DoMes doMes(YearMonth competencia) {
		LocalDate inicio = competencia.atDay(1);
		LocalDate fim = competencia.atEndOfMonth();
		List<Lancamento> ateOFim = lancamentos.validosAte(fim);
		List<Lancamento> doMes = ateOFim.stream().filter(l -> !l.getData().isBefore(inicio)).toList();

		List<MovimentoDaConta> movimentos = contas.findAllByOrderByNome().stream()
				.filter(c -> !c.getDataSaldoInicial().isAfter(fim))
				.map(c -> movimento(c, ateOFim, doMes, inicio, fim))
				.toList();

		List<TotalDaCategoria> receitas = porCategoria(doMes, Natureza.RECEITA);
		List<TotalDaCategoria> despesas = porCategoria(doMes, Natureza.DESPESA);
		BigDecimal totalReceitas = somar(receitas);
		BigDecimal totalDespesas = somar(despesas);
		BigDecimal saldoTotal = movimentos.stream().map(MovimentoDaConta::saldoFinal).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal inadimplencia = cobrancas.vencidasEm(fim.plusDays(1)).stream()
				.map(c -> c.getValor()).reduce(BigDecimal.ZERO, BigDecimal::add);
		return new DoMes(competencia, movimentos, receitas, totalReceitas, despesas, totalDespesas,
				totalReceitas.subtract(totalDespesas), saldoTotal, inadimplencia);
	}

	private static MovimentoDaConta movimento(ContaFinanceira c, List<Lancamento> ateOFim, List<Lancamento> doMes,
			LocalDate inicio, LocalDate fim) {
		BigDecimal anterior = ConsultaDoCaixa.saldo(c, ateOFim, inicio.minusDays(1));
		if (c.getDataSaldoInicial().isAfter(inicio.minusDays(1))) {
			anterior = c.getSaldoInicial(); // a conta começou neste mês
		}
		List<Lancamento> daConta = doMes.stream().filter(l -> l.getConta().getId().equals(c.getId())).toList();
		BigDecimal entradas = daConta.stream().filter(l -> l.getTipo().sinal() > 0).map(Lancamento::getValor)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal saidas = daConta.stream().filter(l -> l.getTipo().sinal() < 0).map(Lancamento::getValor)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		return new MovimentoDaConta(c.getNome(), anterior, entradas, saidas, anterior.add(entradas).subtract(saidas));
	}

	private static List<TotalDaCategoria> porCategoria(List<Lancamento> doMes, Natureza natureza) {
		Map<String, BigDecimal> totais = doMes.stream()
				.filter(l -> l.getTipo().natureza() == natureza)
				.collect(Collectors.groupingBy(l -> l.getCategoria().getNome(), TreeMap::new,
						Collectors.reducing(BigDecimal.ZERO, Lancamento::getValor, BigDecimal::add)));
		return totais.entrySet().stream()
				.map(e -> new TotalDaCategoria(e.getKey(), e.getValue()))
				.sorted(Comparator.comparing(TotalDaCategoria::total).reversed())
				.toList();
	}

	private static BigDecimal somar(List<TotalDaCategoria> totais) {
		return totais.stream().map(TotalDaCategoria::total).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

}
