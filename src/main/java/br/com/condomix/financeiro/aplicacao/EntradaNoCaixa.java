package br.com.condomix.financeiro.aplicacao;

import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.financeiro.dominio.CategoriaPadrao;
import br.com.condomix.financeiro.dominio.Cobranca;
import br.com.condomix.financeiro.dominio.ContaFinanceira;
import br.com.condomix.financeiro.dominio.ItemDeCobranca;
import br.com.condomix.financeiro.dominio.Lancamento;
import br.com.condomix.financeiro.dominio.OrigemDoLancamento;
import br.com.condomix.financeiro.dominio.PagamentoDeCobrancaEstornado;
import br.com.condomix.financeiro.dominio.PagamentoDeCobrancaRegistrado;
import br.com.condomix.financeiro.dominio.TipoDeLancamento;
import br.com.condomix.financeiro.persistencia.CobrancaRepository;
import br.com.condomix.financeiro.persistencia.LancamentoRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Leva os pagamentos das cobranças para o caixa, separados por categoria, para o balancete mostrar
 * de onde veio o dinheiro (taxa, fundo de reserva, taxa extra, multas e juros...).
 *
 * <p>Divisão do valor pago:
 * <ul>
 *   <li>cada item da cobrança vai para a categoria do seu tipo; o desconto de mandato reduz a taxa condominial;</li>
 *   <li>o que foi pago <b>a mais</b> que o valor da cobrança é multa e juros por atraso;</li>
 *   <li>o que foi pago <b>a menos</b> (encargos ou parte perdoados) sai das maiores parcelas primeiro.</li>
 * </ul>
 * Roda na mesma transação da baixa: se falhar, a baixa também não acontece.
 */
@Component
class EntradaNoCaixa {

	private final CobrancaRepository cobrancas;

	private final LancamentoRepository lancamentos;

	private final PlanoDeContas plano;

	private final Clock relogio;

	EntradaNoCaixa(CobrancaRepository cobrancas, LancamentoRepository lancamentos, PlanoDeContas plano, Clock relogio) {
		this.cobrancas = cobrancas;
		this.lancamentos = lancamentos;
		this.plano = plano;
		this.relogio = relogio;
	}

	@EventListener
	void pagamentoRegistrado(PagamentoDeCobrancaRegistrado evento) {
		Cobranca cobranca = cobrancas.findById(evento.cobrancaId()).orElseThrow();
		ContaFinanceira conta = plano.contaDeRecebimento(cobranca.getPagoEm());
		UUID tenant = TenantAtual.obrigatorio();
		parcelas(cobranca).forEach((categoria, valor) -> {
			if (valor.signum() > 0) {
				lancamentos.save(Regras.aplicar(() -> Lancamento.movimento(tenant, conta, plano.categoria(categoria),
						TipoDeLancamento.RECEITA, OrigemDoLancamento.COBRANCA, cobranca.getPagoEm(),
						cobranca.getDescricao(), valor, null, cobranca.getId(), null)));
			}
		});
	}

	@EventListener
	void pagamentoEstornado(PagamentoDeCobrancaEstornado evento) {
		Instant agora = Instant.now(relogio);
		lancamentos.findByCobrancaIdAndCanceladoEmIsNull(evento.cobrancaId())
				.forEach(l -> l.cancelar("Pagamento da cobrança estornado", agora));
	}

	static Map<CategoriaPadrao, BigDecimal> parcelas(Cobranca cobranca) {
		Map<CategoriaPadrao, BigDecimal> parcelas = new EnumMap<>(CategoriaPadrao.class);
		for (ItemDeCobranca item : cobranca.getItens()) {
			parcelas.merge(item.tipo().categoriaNoCaixa(), item.valor(), BigDecimal::add);
		}
		BigDecimal diferenca = cobranca.getValorPago().subtract(cobranca.getValor());
		if (diferenca.signum() > 0) {
			parcelas.merge(CategoriaPadrao.MULTAS_E_JUROS, diferenca, BigDecimal::add);
		}
		BigDecimal falta = diferenca.signum() < 0 ? diferenca.negate() : BigDecimal.ZERO;
		while (falta.signum() > 0) {
			var maior = parcelas.entrySet().stream().max(Comparator.comparing(Map.Entry::getValue)).orElseThrow();
			BigDecimal tirar = maior.getValue().min(falta);
			maior.setValue(maior.getValue().subtract(tirar));
			falta = falta.subtract(tirar);
		}
		return parcelas;
	}

}
