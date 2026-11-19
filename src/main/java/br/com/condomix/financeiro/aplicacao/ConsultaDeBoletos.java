package br.com.condomix.financeiro.aplicacao;

import br.com.condomix.acesso.CadastroParaCobranca;
import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.financeiro.dominio.Boleto;
import br.com.condomix.financeiro.dominio.Cobranca;
import br.com.condomix.financeiro.dominio.StatusCobranca;
import br.com.condomix.financeiro.persistencia.BoletoRepository;
import br.com.condomix.financeiro.persistencia.CobrancaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/** Caso de uso: a 2ª via. O boleto e o Pix de uma cobrança em aberto, para pagar pelo app. */
@Service
@Transactional(readOnly = true)
public class ConsultaDeBoletos {

	private final BoletoRepository boletos;

	private final CobrancaRepository cobrancas;

	private final CadastroParaCobranca cadastro;

	private final Clock relogio;

	public ConsultaDeBoletos(BoletoRepository boletos, CobrancaRepository cobrancas, CadastroParaCobranca cadastro,
			Clock relogio) {
		this.boletos = boletos;
		this.cobrancas = cobrancas;
		this.cadastro = cadastro;
		this.relogio = relogio;
	}

	/** Para a gestão (síndico, conselho, suporte). */
	public Boleto daCobranca(UUID cobrancaId) {
		return pagavel(cobrancas.findById(cobrancaId).orElseThrow(() -> new NaoEncontradoException("Cobrança não encontrada")));
	}

	/** Para o morador: só das unidades de que ele é proprietário ou inquilino ("meus débitos"). */
	public Boleto doPagador(UUID usuarioId, UUID cobrancaId) {
		Cobranca cobranca = cobrancas.findById(cobrancaId)
				.filter(c -> cadastro.unidadesDoPagador(usuarioId, LocalDate.now(relogio)).contains(c.getUnidadeId()))
				.orElseThrow(() -> new NaoEncontradoException("Cobrança não encontrada"));
		return pagavel(cobranca);
	}

	private Boleto pagavel(Cobranca cobranca) {
		if (cobranca.getStatus() != StatusCobranca.ABERTA) {
			throw new NaoEncontradoException("Esta cobrança não está em aberto");
		}
		return boletos.vigente(cobranca.getId())
				.filter(b -> b.getSituacao().pagavel())
				.orElseThrow(() -> new NaoEncontradoException("Esta cobrança ainda não tem boleto"));
	}

}
