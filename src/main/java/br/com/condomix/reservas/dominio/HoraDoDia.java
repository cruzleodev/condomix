package br.com.condomix.reservas.dominio;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalTime;

/**
 * Grava a hora do condomínio como texto "HH:MM". Uma coluna {@code time} seria convertida para UTC pelo Hibernate
 * ({@code hibernate.jdbc.time_zone}), e 23h viraria 2h da manhã.
 */
@Converter
public class HoraDoDia implements AttributeConverter<LocalTime, String> {

	@Override
	public String convertToDatabaseColumn(LocalTime hora) {
		return hora == null ? null : String.format("%02d:%02d", hora.getHour(), hora.getMinute());
	}

	@Override
	public LocalTime convertToEntityAttribute(String texto) {
		return texto == null ? null : LocalTime.parse(texto);
	}

}
