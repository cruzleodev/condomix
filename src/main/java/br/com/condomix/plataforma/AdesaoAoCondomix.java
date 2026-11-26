package br.com.condomix.plataforma;

import br.com.condomix.compartilhado.FormatoBrasileiro;
import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.plataforma.persistencia.CondominioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/**
 * O dia em que o condomínio aderiu ao Condomix (foi cadastrado na plataforma), e até onde ele pode voltar no tempo:
 * - a posse de um mandato não pode ser antes da adesão;
 * - o caixa aceita o histórico de até 12 meses antes da adesão, a partir do 1º dia do mês, para o condomínio
 *   trazer a prestação de contas recente (quem adere no meio do mês consegue fechar o mês inteiro).
 */
@Service
@Transactional(readOnly = true)
public class AdesaoAoCondomix {

	/** Quantos meses antes da adesão o caixa aceita lançamentos. */
	public static final int MESES_DE_HISTORICO = 12;

	private final CondominioRepository condominios;

	private final Clock relogio;

	AdesaoAoCondomix(CondominioRepository condominios, Clock relogio) {
		this.condominios = condominios;
		this.relogio = relogio;
	}

	/** A data de adesão do condomínio atual, no fuso do Condomix. */
	public LocalDate data() {
		return condominios.findById(TenantAtual.obrigatorio())
				.map(c -> LocalDate.ofInstant(c.getCriadoEm(), relogio.getZone()))
				.orElseThrow(() -> new NaoEncontradoException("Condomínio não encontrado"));
	}

	/** O primeiro dia aceito no caixa: o 1º dia do mês, 12 meses antes da adesão (adesão em 13/11/2026 → 01/11/2025). */
	public LocalDate inicioDoHistorico() {
		return data().withDayOfMonth(1).minusMonths(MESES_DE_HISTORICO);
	}

	/**
	 * Recusa uma data anterior ao início do histórico aceito no caixa.
	 * @param oQue como a data aparece na mensagem, ex.: "A data do lançamento"
	 */
	public void exigirDentroDoHistorico(LocalDate data, String oQue) {
		if (data == null) {
			return;
		}
		LocalDate inicio = inicioDoHistorico();
		if (data.isBefore(inicio)) {
			throw RegraDeNegocioException.invalido(oQue + " não pode ser antes de " + FormatoBrasileiro.data(inicio)
					+ ": o Condomix aceita até " + MESES_DE_HISTORICO + " meses antes da adesão do condomínio ("
					+ FormatoBrasileiro.data(data()) + ").");
		}
	}

	/**
	 * Recusa uma data anterior à adesão.
	 * @param oQue como a data aparece na mensagem, ex.: "A posse", "A data do lançamento"
	 */
	public void exigirDesdeAAdesao(LocalDate data, String oQue) {
		if (data == null) {
			return;
		}
		LocalDate adesao = data();
		if (data.isBefore(adesao)) {
			throw RegraDeNegocioException.invalido(oQue + " não pode ser antes de " + FormatoBrasileiro.data(adesao)
					+ ", quando o condomínio foi cadastrado no Condomix.");
		}
	}

}
