package br.com.condomix.financeiro.aplicacao;

import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.financeiro.CobrancasParaReservas;
import br.com.condomix.financeiro.dominio.StatusCobranca;
import br.com.condomix.financeiro.dominio.TipoDeItem;
import br.com.condomix.financeiro.persistencia.CobrancaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** O aluguel das reservas vira cobrança avulsa; cancelar a reserva no prazo cancela a cobrança. */
@Service
@Transactional
class CobrancasParaReservasService implements CobrancasParaReservas {

	private final CobrancaRepository cobrancas;

	private final EmissaoAvulsa avulsa;

	private final BaixaDeCobrancas baixa;

	CobrancasParaReservasService(CobrancaRepository cobrancas, EmissaoAvulsa avulsa, BaixaDeCobrancas baixa) {
		this.cobrancas = cobrancas;
		this.avulsa = avulsa;
		this.baixa = baixa;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean unidadeInadimplente(UUID unidadeId, LocalDate hoje) {
		return cobrancas.existsByUnidadeIdAndStatusAndVencimentoBefore(unidadeId, StatusCobranca.ABERTA, hoje);
	}

	@Override
	public UUID cobrarAluguel(UUID unidadeId, String descricao, BigDecimal valor, LocalDate vencimento, UUID criadaPor) {
		return avulsa.emitir(unidadeId, TipoDeItem.RESERVA_DE_ESPACO, descricao, valor, vencimento, criadaPor).getId();
	}

	@Override
	public ResultadoDoCancelamento cancelarAluguel(UUID cobrancaId, String motivo) {
		var cobranca = cobrancas.findById(cobrancaId).orElseThrow(() -> new NaoEncontradoException("Cobrança não encontrada"));
		if (cobranca.getStatus() == StatusCobranca.PAGA) {
			return ResultadoDoCancelamento.JA_PAGA;
		}
		if (cobranca.getStatus() == StatusCobranca.CANCELADA) {
			return ResultadoDoCancelamento.JA_CANCELADA;
		}
		baixa.cancelar(cobrancaId, motivo);
		return ResultadoDoCancelamento.CANCELADA;
	}

}
