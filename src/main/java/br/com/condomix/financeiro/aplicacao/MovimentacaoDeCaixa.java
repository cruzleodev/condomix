package br.com.condomix.financeiro.aplicacao;

import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.financeiro.dominio.CategoriaFinanceira;
import br.com.condomix.financeiro.dominio.ContaFinanceira;
import br.com.condomix.financeiro.dominio.Lancamento;
import br.com.condomix.financeiro.dominio.OrigemDoLancamento;
import br.com.condomix.financeiro.dominio.TipoDeLancamento;
import br.com.condomix.financeiro.persistencia.LancamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Caso de uso: o síndico lança despesas, receitas avulsas e transferências entre contas, e cancela lançamentos errados. */
@Service
@Transactional
public class MovimentacaoDeCaixa {

	private final LancamentoRepository lancamentos;

	private final PlanoDeContas plano;

	private final Clock relogio;

	public MovimentacaoDeCaixa(LancamentoRepository lancamentos, PlanoDeContas plano, Clock relogio) {
		this.lancamentos = lancamentos;
		this.plano = plano;
		this.relogio = relogio;
	}

	/** @param tipo RECEITA ou DESPESA; a categoria precisa ser da mesma natureza */
	public record Movimento(TipoDeLancamento tipo, UUID contaId, UUID categoriaId, LocalDate data, String descricao,
			BigDecimal valor, String favorecido) {
	}

	public Lancamento lancar(Movimento m, UUID criadoPor) {
		ContaFinanceira conta = plano.buscarConta(m.contaId());
		CategoriaFinanceira categoria = plano.buscarCategoria(m.categoriaId());
		if (!categoria.isAtiva()) {
			throw RegraDeNegocioException.invalido("A categoria " + categoria.getNome() + " está desativada");
		}
		LocalDate data = m.data() != null ? m.data() : LocalDate.now(relogio);
		return lancamentos.save(Regras.aplicar(() -> Lancamento.movimento(TenantAtual.obrigatorio(), conta, categoria,
				m.tipo(), OrigemDoLancamento.MANUAL, data, m.descricao(), m.valor(), m.favorecido(), null, criadoPor)));
	}

	/** Move dinheiro entre duas contas (ex.: aplicar no fundo de reserva). Não é receita nem despesa. */
	public List<Lancamento> transferir(UUID origemId, UUID destinoId, LocalDate data, BigDecimal valor, String descricao,
			UUID criadoPor) {
		if (origemId.equals(destinoId)) {
			throw RegraDeNegocioException.invalido("A conta de origem e a de destino precisam ser diferentes");
		}
		ContaFinanceira origem = plano.buscarConta(origemId);
		ContaFinanceira destino = plano.buscarConta(destinoId);
		LocalDate quando = data != null ? data : LocalDate.now(relogio);
		String texto = descricao == null || descricao.isBlank()
				? "Transferência " + origem.getNome() + " → " + destino.getNome() : descricao;
		UUID transferencia = UUID.randomUUID();
		UUID tenant = TenantAtual.obrigatorio();
		return Regras.aplicar(() -> List.of(
				lancamentos.save(Lancamento.transferencia(tenant, origem, TipoDeLancamento.TRANSFERENCIA_SAIDA, quando, texto,
						valor, transferencia, criadoPor)),
				lancamentos.save(Lancamento.transferencia(tenant, destino, TipoDeLancamento.TRANSFERENCIA_ENTRADA, quando, texto,
						valor, transferencia, criadoPor))));
	}

	/**
	 * Cancela um lançamento manual. Numa transferência, cancela as duas pontas. Lançamentos de cobrança
	 * não se cancelam aqui: estorne o pagamento da cobrança.
	 */
	public void cancelar(UUID id, String motivo) {
		Lancamento lancamento = lancamentos.findById(id)
				.orElseThrow(() -> new NaoEncontradoException("Lançamento não encontrado"));
		if (lancamento.getOrigem() == OrigemDoLancamento.COBRANCA) {
			throw RegraDeNegocioException.invalido("Este lançamento veio do pagamento de uma cobrança: estorne o pagamento da cobrança");
		}
		Instant agora = Instant.now(relogio);
		List<Lancamento> afetados = lancamento.getTransferenciaId() == null ? List.of(lancamento)
				: lancamentos.findByTransferenciaId(lancamento.getTransferenciaId());
		afetados.forEach(l -> Regras.aplicar(() -> l.cancelar(motivo, agora)));
	}

}
