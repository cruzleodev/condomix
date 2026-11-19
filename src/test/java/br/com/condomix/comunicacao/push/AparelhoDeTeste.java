package br.com.condomix.comunicacao.push;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import java.util.Base64;

/**
 * Faz o papel do navegador inscrito no push: tem as chaves do aparelho e decifra a mensagem (RFC 8291), como o
 * navegador faria. Serve para os testes conferirem o que o Condomix enviou.
 */
public final class AparelhoDeTeste {

	private final KeyPair chaves = CriptografiaWebPush.novoParDeChaves();

	private final byte[] segredo = new byte[16];

	public AparelhoDeTeste() {
		new SecureRandom().nextBytes(segredo);
	}

	public String p256dh() {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(CriptografiaWebPush.pontoSemCompressao((ECPublicKey) chaves.getPublic()));
	}

	public String auth() {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(segredo);
	}

	public String decifrar(byte[] corpo) throws Exception {
		ByteBuffer leitura = ByteBuffer.wrap(corpo);
		byte[] sal = new byte[16];
		leitura.get(sal);
		leitura.getInt(); // tamanho do registro
		byte[] chaveDoServidor = new byte[leitura.get()];
		leitura.get(chaveDoServidor);
		byte[] cifrado = new byte[leitura.remaining()];
		leitura.get(cifrado);

		KeyAgreement acordo = KeyAgreement.getInstance("ECDH");
		acordo.init(chaves.getPrivate());
		acordo.doPhase(CriptografiaWebPush.chavePublica(chaveDoServidor, (ECPublicKey) chaves.getPublic()), true);
		byte[] meuPonto = CriptografiaWebPush.pontoSemCompressao((ECPublicKey) chaves.getPublic());
		byte[] prkChave = CriptografiaWebPush.hmac(segredo, acordo.generateSecret());
		byte[] ikm = CriptografiaWebPush.hmac(prkChave, CriptografiaWebPush.concatenar(
				"WebPush: info\0".getBytes(StandardCharsets.US_ASCII), meuPonto, chaveDoServidor, new byte[] { 1 }));
		byte[] prk = CriptografiaWebPush.hmac(sal, ikm);
		byte[] cek = Arrays.copyOf(CriptografiaWebPush.hmac(prk, CriptografiaWebPush.concatenar(
				"Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[] { 1 })), 16);
		byte[] nonce = Arrays.copyOf(CriptografiaWebPush.hmac(prk, CriptografiaWebPush.concatenar(
				"Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[] { 1 })), 12);
		Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
		cifra.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
		byte[] claro = cifra.doFinal(cifrado);
		if (claro[claro.length - 1] != 2) {
			throw new IllegalStateException("Falta o delimitador do último registro");
		}
		return new String(claro, 0, claro.length - 1, StandardCharsets.UTF_8);
	}

}
