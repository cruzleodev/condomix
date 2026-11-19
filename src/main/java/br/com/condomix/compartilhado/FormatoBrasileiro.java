package br.com.condomix.compartilhado;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.Locale;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/** Datas, horas e valores nas mensagens para o usuário, no formato brasileiro: 19/01/2027, 12/2026, 18h, R$ 1.234,50. */
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

	/** "R$ 1.234,50". */
	public static String moeda(BigDecimal valor) {
		if (valor == null) {
			return "";
		}
		NumberFormat formato = NumberFormat.getCurrencyInstance(Locale.of("pt", "BR"));
		return formato.format(valor).replace('\u00A0', ' ');
	}

	/** "18h", "18h30". */
	public static String hora(LocalTime hora) {
		if (hora == null) {
			return "";
		}
		return hora.getMinute() == 0 ? hora.getHour() + "h" : hora.getHour() + "h" + String.format("%02d", hora.getMinute());
	}

	/** "sábado, 20/12/2026". */
	public static String diaDaSemana(LocalDate data) {
		if (data == null) {
			return "";
		}
		return data.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.of("pt", "BR")) + ", " + data(data);
	}

}
