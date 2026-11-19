package br.com.condomix.financeiro.aplicacao;

import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.financeiro.dominio.Cobranca;
import br.com.condomix.financeiro.dominio.CobrancaCancelada;
import br.com.condomix.financeiro.dominio.FormaDePagamento;
import br.com.condomix.financeiro.dominio.PagamentoDeCobrancaEstornado;
import br.com.condomix.financeiro.dominio.PagamentoDeCobrancaRegistrado;
import br.com.condomix.financeiro.persistencia.CobrancaRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Caso de uso: o síndico registra que recebeu (baixa manual), desfaz uma baixa feita por engano ou cancela
 * uma cobrança. Também registra a baixa automática, quando o gateway confirma o pagamento.
 *
 * <p>Publica {@link PagamentoDeCobrancaRegistrado} e {@link PagamentoDeCobrancaEstornado}: o caixa reage
 * na mesma transação (se a entrada no caixa falhar, a baixa também não acontece).
 */
@Service
@Transactional
public class BaixaDeCobrancas {

	private final CobrancaRepository cobrancas;

	private final ApplicationEventPublisher eventos;

	private final Clock relogio;

	public BaixaDeCobrancas(CobrancaRepository cobrancas, ApplicationEventPublisher eventos, Clock relogio) {
		this.cobrancas = cobrancas;
		this.eventos = eventos;
		this.relogio = relogio;
	}

	/** @param valorPago vazio = valor com multa e juros na data do pagamento; menor que isso = encargos perdoados */
	public Cobranca registrarPagamento(UUID id, LocalDate data, BigDecimal valorPago, FormaDePagamento forma,
			String observacao, UUID registradoPor) {
		Cobranca cobranca = buscar(id);
		LocalDate pagoEm = data != null ? data : LocalDate.now(relogio);
		if (pagoEm.isAfter(LocalDate.now(relogio))) {
			throw RegraDeNegocioException.invalido("A data do pagamento não pode ser no futuro");
		}
		Regras.aplicar(() -> cobranca.registrarPagamento(pagoEm, valorPago, forma, observacao, registradoPor));
		eventos.publishEvent(PagamentoDeCobrancaRegistrado.manual(cobranca.getId()));
		return cobranca;
	}

	/**
	 * Baixa automática: o gateway confirmou o pagamento do boleto ou do Pix. O valor pago já inclui multa e juros,
	 * se o morador pagou atrasado; a tarifa do gateway vira despesa no caixa.
	 */
	public Cobranca registrarPagamentoPeloGateway(UUID id, LocalDate data, BigDecimal valorPago, BigDecimal tarifa,
			FormaDePagamento forma, String observacao) {
		Cobranca cobranca = buscar(id);
		Regras.aplicar(() -> cobranca.registrarPagamento(data, valorPago, forma, observacao, null));
		eventos.publishEvent(new PagamentoDeCobrancaRegistrado(cobranca.getId(), true, tarifa));
		return cobranca;
	}

	public Cobranca estornarPagamento(UUID id) {
		Cobranca cobranca = buscar(id);
		Regras.aplicar(cobranca::estornarPagamento);
		eventos.publishEvent(new PagamentoDeCobrancaEstornado(cobranca.getId()));
		return cobranca;
	}

	public Cobranca cancelar(UUID id, String motivo) {
		Cobranca cobranca = buscar(id);
		Regras.aplicar(() -> cobranca.cancelar(motivo, Instant.now(relogio)));
		eventos.publishEvent(new CobrancaCancelada(cobranca.getId()));
		return cobranca;
	}

	private Cobranca buscar(UUID id) {
		return cobrancas.findById(id).orElseThrow(() -> new NaoEncontradoException("Cobrança não encontrada"));
	}

}
