package br.com.condomix.acesso.convite;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Gera códigos de convite impossíveis de adivinhar e calcula a impressão digital que vai para o banco. */
final class CodigoDeConvite {

	private static final SecureRandom ALEATORIO = new SecureRandom();

	private CodigoDeConvite() {
	}

	/** 32 bytes aleatórios, em texto seguro para links (43 caracteres). */
	static String gerar() {
		byte[] bytes = new byte[32];
		ALEATORIO.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String impressaoDigital(String codigo) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(codigo.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
