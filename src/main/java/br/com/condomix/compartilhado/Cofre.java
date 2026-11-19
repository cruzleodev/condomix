package br.com.condomix.compartilhado;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Guarda segredos no banco (ex.: a chave de API do gateway de cada condomínio) cifrados com AES-GCM.
 * A chave do cofre vem de {@code condomix.cofre.chave} (32 bytes em Base64), fora do banco: quem lê só o
 * banco não consegue usar os segredos. Também gera tokens aleatórios e a impressão digital (SHA-256) deles.
 */
@Component
public class Cofre {

	private static final int TAMANHO_DO_IV = 12;

	private static final SecureRandom ALEATORIO = new SecureRandom();

	private final String chaveEmBase64;

	public Cofre(@Value("${condomix.cofre.chave:}") String chaveEmBase64) {
		this.chaveEmBase64 = chaveEmBase64;
	}

	public String cifrar(String segredo) {
		try {
			byte[] iv = new byte[TAMANHO_DO_IV];
			ALEATORIO.nextBytes(iv);
			Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
			cifra.init(Cipher.ENCRYPT_MODE, chave(), new GCMParameterSpec(128, iv));
			byte[] cifrado = cifra.doFinal(segredo.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array());
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("Não foi possível cifrar o segredo", e);
		}
	}

	public String decifrar(String guardado) {
		try {
			byte[] tudo = Base64.getDecoder().decode(guardado);
			Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
			cifra.init(Cipher.DECRYPT_MODE, chave(), new GCMParameterSpec(128, tudo, 0, TAMANHO_DO_IV));
			return new String(cifra.doFinal(tudo, TAMANHO_DO_IV, tudo.length - TAMANHO_DO_IV), StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("Não foi possível decifrar o segredo (a chave do cofre mudou?)", e);
		}
	}

	/** Um token aleatório (256 bits) para usar em URL ou cabeçalho. */
	public static String tokenAleatorio() {
		byte[] bytes = new byte[32];
		ALEATORIO.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public static String impressaoDigital(String token) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Compara em tempo constante (não revela, pelo tempo de resposta, quantos caracteres acertou). */
	public static boolean confere(String token, String impressaoDigitalGuardada) {
		if (token == null || impressaoDigitalGuardada == null) {
			return false;
		}
		return MessageDigest.isEqual(impressaoDigital(token).getBytes(StandardCharsets.UTF_8),
				impressaoDigitalGuardada.getBytes(StandardCharsets.UTF_8));
	}

	private SecretKeySpec chave() {
		if (chaveEmBase64 == null || chaveEmBase64.isBlank()) {
			throw new IllegalStateException("Configure condomix.cofre.chave (32 bytes em Base64) para guardar segredos");
		}
		byte[] bytes = Base64.getDecoder().decode(chaveEmBase64);
		if (bytes.length != 32) {
			throw new IllegalStateException("condomix.cofre.chave deve ter 32 bytes (em Base64)");
		}
		return new SecretKeySpec(bytes, "AES");
	}

}
