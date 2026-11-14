package br.com.condomix.acesso.mandato;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/** A situação de um mandato numa data. Calculada, não gravada (ver {@link Mandato#situacaoEm}). */
public enum SituacaoMandato {

	/** A posse ainda não aconteceu. */
	FUTURO(false),
	VIGENTE(true),
	/** Vigente, mas termina em até {@link Mandato#DIAS_DE_AVISO} dias: hora de convocar a assembleia. */
	A_VENCER(true) {
		@Override
		Optional<String> aviso(LocalDate data, LocalDate fimPrevisto) {
			return Optional.of("O mandato termina em " + ChronoUnit.DAYS.between(data, fimPrevisto) + " dias ("
					+ fimPrevisto + "). Convoque a assembleia para eleger o sucessor.");
		}
	},
	/** O prazo acabou e ninguém tomou posse: continua valendo até o sucessor assumir (decisão de negócio). */
	PRORROGADO(true) {
		@Override
		Optional<String> aviso(LocalDate data, LocalDate fimPrevisto) {
			return Optional.of("Mandato vencido em " + fimPrevisto
					+ ". Prorrogado até a posse do sucessor: convoque a assembleia para a eleição.");
		}
	},
	ENCERRADO(false);

	private final boolean daAcesso;

	SituacaoMandato(boolean daAcesso) {
		this.daAcesso = daAcesso;
	}

	/** Se o ocupante tem os poderes do cargo nesta situação. */
	public boolean daAcesso() {
		return daAcesso;
	}

	/** Mensagem quando há algo a fazer. Cada situação define a sua (sem switch: SOLID, O). */
	Optional<String> aviso(LocalDate data, LocalDate fimPrevisto) {
		return Optional.empty();
	}

}
