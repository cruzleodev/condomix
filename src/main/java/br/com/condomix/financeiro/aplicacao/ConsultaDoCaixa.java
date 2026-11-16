package br.com.condomix.financeiro.aplicacao;

import br.com.condomix.financeiro.dominio.ContaFinanceira;
import br.com.condomix.financeiro.dominio.Lancamento;
import br.com.condomix.financeiro.persistencia.ContaFinanceiraRepository;
import br.com.condomix.financeiro.persistencia.LancamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Caso de uso: saldo de cada conta e extrato dos lançamentos. */
@Service
@Transactional(readOnly = true)
public class ConsultaDoCaixa {

	private final ContaFinanceiraRepository contas;

	private final LancamentoRepository lancamentos;

	private final Clock relogio;

	public ConsultaDoCaixa(ContaFinanceiraRepository contas, LancamentoRepository lancamentos, Clock relogio) {
		this.contas = contas;
		this.lancamentos = lancamentos;
		this.relogio = relogio;
	}

	public record SaldoDaConta(ContaFinanceira conta, BigDecimal saldo) {
	}

	/** Saldo de cada conta no fim do dia {@code data} (vazio = hoje). */
	public List<SaldoDaConta> saldos(LocalDate data) {
		LocalDate ate = data != null ? data : LocalDate.now(relogio);
		List<Lancamento> validos = lancamentos.validosAte(ate);
		return contas.findAllByOrderByNome().stream()
				.map(c -> new SaldoDaConta(c, saldo(c, validos, ate)))
				.toList();
	}

	/** Lançamentos do período, mais recentes primeiro, inclusive os cancelados (aparecem marcados). */
	public List<Lancamento> extrato(LocalDate de, LocalDate ate, UUID contaId) {
		return lancamentos.doPeriodo(de, ate).stream()
				.filter(l -> contaId == null || l.getConta().getId().equals(contaId))
				.toList();
	}

	static BigDecimal saldo(ContaFinanceira conta, List<Lancamento> validos, LocalDate ate) {
		if (ate.isBefore(conta.getDataSaldoInicial())) {
			return BigDecimal.ZERO.setScale(2);
		}
		return validos.stream()
				.filter(l -> l.getConta().getId().equals(conta.getId()) && !l.getData().isAfter(ate))
				.map(Lancamento::valorComSinal)
				.reduce(conta.getSaldoInicial(), BigDecimal::add);
	}

}
