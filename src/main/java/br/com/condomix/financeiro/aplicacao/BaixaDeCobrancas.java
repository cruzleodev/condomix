package br.com.condomix.financeiro.aplicacao;

import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.financeiro.dominio.Cobranca;
import br.com.condomix.financeiro.dominio.FormaDePagamento;
import br.com.condomix.financeiro.persistencia.CobrancaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Caso de uso: o síndico registra que recebeu (baixa manual), desfaz uma baixa feita por engano ou cancela
 * uma cobrança. A baixa automática pelo banco vem na etapa do gateway.
 */
@Service
@Transactional
public class BaixaDeCobrancas {

	private final CobrancaRepository cobrancas;

	private final Clock relogio;

	public BaixaDeCobrancas(CobrancaRepository cobrancas, Clock relogio) {
		this.cobrancas = cobrancas;
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
		return cobranca;
	}

	public Cobranca estornarPagamento(UUID id) {
		Cobranca cobranca = buscar(id);
		Regras.aplicar(cobranca::estornarPagamento);
		return cobranca;
	}

	public Cobranca cancelar(UUID id, String motivo) {
		Cobranca cobranca = buscar(id);
		Regras.aplicar(() -> cobranca.cancelar(motivo, Instant.now(relogio)));
		return cobranca;
	}

	private Cobranca buscar(UUID id) {
		return cobrancas.findById(id).orElseThrow(() -> new NaoEncontradoException("Cobrança não encontrada"));
	}

}
