package br.com.condomix.comunicacao.push;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

/**
 * O Web Push feito só com o Java (sem biblioteca externa):
 * <ul>
 *   <li>cifra a mensagem para o aparelho (RFC 8291, "aes128gcm"): só o navegador inscrito consegue ler;</li>
 *   <li>assina o pedido com a chave VAPID do Condomix (RFC 8292, JWT ES256): o serviço de push sabe quem envia.</li>
 * </ul>
 */
public final class CriptografiaWebPush {

	private static final SecureRandom ALEATORIO = new SecureRandom();

	private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

	private static final Base64.Decoder D64 = Base64.getUrlDecoder();

	private CriptografiaWebPush() {
	}

	/** Um par de chaves P-256 novo (VAPID). */
	public static KeyPair novoParDeChaves() {
		try {
			KeyPairGenerator gerador = KeyPairGenerator.getInstance("EC");
			gerador.initialize(new ECGenParameterSpec("secp256r1"));
			return gerador.generateKeyPair();
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	/** A chave pública como o navegador pede: ponto sem compressão (65 bytes) em Base64 URL. */
	public static String chavePublicaEmBase64(ECPublicKey chave) {
		return B64.encodeToString(pontoSemCompressao(chave));
	}

	public static PrivateKey chavePrivada(byte[] pkcs8) {
		try {
			return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Cifra a mensagem para o aparelho inscrito.
	 *
	 * @param p256dh a chave pública do aparelho (Base64 URL), da inscrição
	 * @param auth   o segredo de autenticação do aparelho (Base64 URL), da inscrição
	 * @return o corpo do pedido HTTP (cabeçalho aes128gcm + texto cifrado)
	 */
	public static byte[] cifrar(byte[] mensagem, String p256dh, String auth) {
		try {
			byte[] chaveDoAparelho = D64.decode(p256dh);
			byte[] segredo = D64.decode(auth);
			KeyPair efemera = novoParDeChaves();
			byte[] nossaChave = pontoSemCompressao((ECPublicKey) efemera.getPublic());

			KeyAgreement acordo = KeyAgreement.getInstance("ECDH");
			acordo.init(efemera.getPrivate());
			acordo.doPhase(chavePublica(chaveDoAparelho, (ECPublicKey) efemera.getPublic()), true);
			byte[] segredoCompartilhado = acordo.generateSecret();

			byte[] prkChave = hmac(segredo, segredoCompartilhado);
			byte[] infoChave = concatenar("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), chaveDoAparelho, nossaChave);
			byte[] ikm = hmac(prkChave, concatenar(infoChave, new byte[] { 1 }));

			byte[] sal = new byte[16];
			ALEATORIO.nextBytes(sal);
			byte[] prk = hmac(sal, ikm);
			byte[] cek = Arrays.copyOf(hmac(prk, concatenar("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII),
					new byte[] { 1 })), 16);
			byte[] nonce = Arrays.copyOf(hmac(prk, concatenar("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII),
					new byte[] { 1 })), 12);

			Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
			cifra.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
			byte[] cifrado = cifra.doFinal(concatenar(mensagem, new byte[] { 2 })); // 2: último registro

			ByteBuffer cabecalho = ByteBuffer.allocate(16 + 4 + 1 + nossaChave.length);
			cabecalho.put(sal).putInt(4096).put((byte) nossaChave.length).put(nossaChave);
			return concatenar(cabecalho.array(), cifrado);
		}
		catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new IllegalStateException("Não foi possível cifrar a mensagem do push: " + e.getMessage(), e);
		}
	}

	/**
	 * O cabeçalho {@code Authorization} do pedido: um JWT assinado com a chave VAPID, para a origem do serviço de push.
	 *
	 * @param contato e-mail ("mailto:...") ou site de contato do Condomix, exigido pelos serviços de push
	 */
	public static String autorizacao(String endpoint, PrivateKey chavePrivada, String chavePublica, String contato,
			Instant expiraEm) {
		try {
			URI uri = URI.create(endpoint);
			String origem = uri.getScheme() + "://" + uri.getAuthority();
			String cabecalho = B64.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
			String corpo = B64.encodeToString("{\"aud\":\"%s\",\"exp\":%d,\"sub\":\"%s\"}"
					.formatted(origem, expiraEm.getEpochSecond(), contato).getBytes(StandardCharsets.UTF_8));
			Signature assinatura = Signature.getInstance("SHA256withECDSAinP1363Format");
			assinatura.initSign(chavePrivada);
			assinatura.update((cabecalho + "." + corpo).getBytes(StandardCharsets.US_ASCII));
			String jwt = cabecalho + "." + corpo + "." + B64.encodeToString(assinatura.sign());
			return "vapid t=" + jwt + ", k=" + chavePublica;
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	static byte[] pontoSemCompressao(ECPublicKey chave) {
		ByteBuffer ponto = ByteBuffer.allocate(65);
		ponto.put((byte) 4).put(semSinal(chave.getW().getAffineX())).put(semSinal(chave.getW().getAffineY()));
		return ponto.array();
	}

	static ECPublicKey chavePublica(byte[] ponto, ECPublicKey referencia) throws GeneralSecurityException {
		if (ponto.length != 65 || ponto[0] != 4) {
			throw new IllegalArgumentException("chave do aparelho inválida");
		}
		ECParameterSpec curva = referencia.getParams();
		ECPoint w = new ECPoint(new BigInteger(1, Arrays.copyOfRange(ponto, 1, 33)), new BigInteger(1, Arrays.copyOfRange(ponto, 33, 65)));
		return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w, curva));
	}

	static byte[] hmac(byte[] chave, byte[] dados) throws GeneralSecurityException {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(chave, "HmacSHA256"));
		return mac.doFinal(dados);
	}

	static byte[] concatenar(byte[]... partes) {
		ByteArrayOutputStream saida = new ByteArrayOutputStream();
		for (byte[] parte : partes) {
			saida.writeBytes(parte);
		}
		return saida.toByteArray();
	}

	/** Coordenada em 32 bytes, sem o byte de sinal do BigInteger. */
	private static byte[] semSinal(BigInteger numero) {
		byte[] bytes = numero.toByteArray();
		byte[] resultado = new byte[32];
		int copiar = Math.min(bytes.length, 32);
		System.arraycopy(bytes, bytes.length - copiar, resultado, 32 - copiar, copiar);
		return resultado;
	}

}
