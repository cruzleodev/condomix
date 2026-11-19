package br.com.condomix.compartilhado;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/** Datas nas mensagens para o usuário, no formato brasileiro: 19/01/2027 e 12/2026. */
public final class FormatoBrasileiro {

	private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MM/yyyy");

	private FormatoBrasileiro() {
	}

	public static String data(LocalDate data) {
		return data == null ? "" : DATA.format(data);
	}

	public static String mes(YearMonth mes) {
		return mes == null ? "" : MES.format(mes);
	}

}
