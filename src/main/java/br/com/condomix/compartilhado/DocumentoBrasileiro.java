package br.com.condomix.compartilhado;

import java.util.Locale;

/**
 * CPF e CNPJ: limpa a pontuação e confere os dígitos verificadores.
 *
 * <p>Aceita o <b>CNPJ alfanumérico</b> (Receita Federal, a partir de julho de 2026): os 12 primeiros
 * caracteres podem ter letras, e os 2 últimos continuam sendo números. No cálculo, cada caractere vale
 * o seu código ASCII menos 48 (os números continuam valendo eles mesmos; "A" vale 17).
 */
public final class DocumentoBrasileiro {

	private static final int[] PESOS_CNPJ_1 = { 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2 };

	private static final int[] PESOS_CNPJ_2 = { 6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2 };

	private DocumentoBrasileiro() {
	}

	/**
	 * Devolve o documento sem pontuação e em maiúsculas, ou lança {@link IllegalArgumentException}
	 * explicando o problema. {@code null} ou vazio devolve {@code null} (documento é opcional).
	 */
	public static String normalizar(String documento) {
		if (documento == null || documento.isBlank()) {
			return null;
		}
		String limpo = documento.replaceAll("[\\s./-]", "").toUpperCase(Locale.ROOT);
		if (limpo.length() == 11 && limpo.chars().allMatch(Character::isDigit)) {
			if (!cpfValido(limpo)) {
				throw new IllegalArgumentException("CPF inválido");
			}
			return limpo;
		}
		if (limpo.length() == 14 && limpo.matches("[0-9A-Z]{12}[0-9]{2}")) {
			if (!cnpjValido(limpo)) {
				throw new IllegalArgumentException("CNPJ inválido");
			}
			return limpo;
		}
		throw new IllegalArgumentException("Documento deve ser um CPF (11 dígitos) ou CNPJ (14 caracteres)");
	}

	public static boolean ehCnpj(String documentoNormalizado) {
		return documentoNormalizado != null && documentoNormalizado.length() == 14;
	}

	private static boolean cpfValido(String cpf) {
		if (cpf.chars().distinct().count() == 1) {
			return false; // 000.000.000-00, 111.111.111-11... passam na conta, mas não existem
		}
		int d1 = digitoCpf(cpf, 9);
		int d2 = digitoCpf(cpf, 10);
		return d1 == cpf.charAt(9) - '0' && d2 == cpf.charAt(10) - '0';
	}

	private static int digitoCpf(String cpf, int quantidade) {
		int soma = 0;
		for (int i = 0; i < quantidade; i++) {
			soma += (cpf.charAt(i) - '0') * (quantidade + 1 - i);
		}
		int resto = soma % 11;
		return resto < 2 ? 0 : 11 - resto;
	}

	private static boolean cnpjValido(String cnpj) {
		if (cnpj.chars().distinct().count() == 1) {
			return false;
		}
		int d1 = digitoCnpj(cnpj, PESOS_CNPJ_1);
		int d2 = digitoCnpj(cnpj, PESOS_CNPJ_2);
		return d1 == cnpj.charAt(12) - '0' && d2 == cnpj.charAt(13) - '0';
	}

	private static int digitoCnpj(String cnpj, int[] pesos) {
		int soma = 0;
		for (int i = 0; i < pesos.length; i++) {
			soma += (cnpj.charAt(i) - '0') * pesos[i];
		}
		int resto = soma % 11;
		return resto < 2 ? 0 : 11 - resto;
	}

}
