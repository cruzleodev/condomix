package br.com.condomix.acesso.mandato;

import br.com.condomix.acesso.persistencia.MandatoRepository;
import br.com.condomix.compartilhado.NaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Caso de uso: quem ocupa (e quem já ocupou) cada cargo, com a situação de hoje. Aberto a todos do condomínio. */
@Service
@Transactional(readOnly = true)
public class ConsultaDeMandatos {

	private final MandatoRepository mandatos;

	private final Clock relogio;

	public ConsultaDeMandatos(MandatoRepository mandatos, Clock relogio) {
		this.mandatos = mandatos;
		this.relogio = relogio;
	}

	/** Um mandato com a situação calculada para hoje. */
	public record MandatoHoje(Mandato mandato, SituacaoMandato situacao, Optional<String> aviso) {
	}

	/** @param somenteComAcesso só os que dão poderes hoje (vigentes, a vencer e prorrogados) */
	public List<MandatoHoje> listar(boolean somenteComAcesso) {
		LocalDate hoje = LocalDate.now(relogio);
		return mandatos.historico().stream()
				.map(m -> new MandatoHoje(m, m.situacaoEm(hoje), m.avisoEm(hoje)))
				.filter(m -> !somenteComAcesso || m.situacao().daAcesso())
				.toList();
	}

	public MandatoHoje obter(UUID id) {
		LocalDate hoje = LocalDate.now(relogio);
		Mandato m = mandatos.comOcupante(id).orElseThrow(() -> new NaoEncontradoException("Mandato não encontrado"));
		return new MandatoHoje(m, m.situacaoEm(hoje), m.avisoEm(hoje));
	}

}
