package br.com.condomix.acesso.mandato;

import br.com.condomix.acesso.Cargo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

record MandatoResposta(
		UUID id,
		Cargo cargo,
		String cargoRotulo,
		Ocupante ocupante,
		LocalDate inicio,
		LocalDate fimPrevisto,
		LocalDate encerradoEm,
		MotivoEncerramento motivoEncerramento,
		SituacaoMandato situacao,
		String aviso,
		UUID ataEleicaoId,
		UUID ataEncerramentoId,
		Beneficio beneficio) {

	record Ocupante(UUID pessoaId, String nome) {
	}

	record Beneficio(BigDecimal descontoPercentual, UUID unidadeId) {
	}

	static MandatoResposta de(ConsultaDeMandatos.MandatoHoje hoje) {
		Mandato m = hoje.mandato();
		return new MandatoResposta(m.getId(), m.getCargo(), m.getCargo().rotulo(),
				new Ocupante(m.getPessoa().getId(), m.getPessoa().getNome()),
				m.getInicio(), m.getFimPrevisto(), m.getEncerradoEm(), m.getMotivoEncerramento(),
				hoje.situacao(), hoje.aviso().orElse(null), m.getAtaEleicaoId(), m.getAtaEncerramentoId(),
				m.getBeneficio().map(b -> new Beneficio(b.percentual(), b.unidadeId())).orElse(null));
	}

}
