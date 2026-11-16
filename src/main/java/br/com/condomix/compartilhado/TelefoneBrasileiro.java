package br.com.condomix.compartilhado;

/**
 * Telefone no formato internacional E.164 sem o "+" (ex.: 5591999999999), que é o formato do WhatsApp.
 * Aceita o número digitado com ou sem DDI, com pontuação.
 */
public final class TelefoneBrasileiro {

	private TelefoneBrasileiro() {
	}

	/** {@code null} ou vazio devolve {@code null}. Número inválido lança {@link IllegalArgumentException}. */
	public static String normalizar(String telefone) {
		if (telefone == null || telefone.isBlank()) {
			return null;
		}
		String digitos = telefone.replaceAll("\\D", "");
		if (digitos.length() == 10 || digitos.length() == 11) {
			digitos = "55" + digitos; // só DDD + número
		}
		if (!digitos.startsWith("55") || (digitos.length() != 12 && digitos.length() != 13)) {
			throw new IllegalArgumentException("Telefone inválido: informe DDD e número, ex.: (91) 99999-9999");
		}
		return digitos;
	}

}
