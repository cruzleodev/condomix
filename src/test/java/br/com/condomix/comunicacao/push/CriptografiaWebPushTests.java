package br.com.condomix.comunicacao.push;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class CriptografiaWebPushTests {

	@Test
	void soOAparelhoInscritoDecifraAMensagem() throws Exception {
		var aparelho = new AparelhoDeTeste();
		byte[] corpo = CriptografiaWebPush.cifrar("{\"titulo\":\"Reserva confirmada\"}".getBytes(StandardCharsets.UTF_8),
				aparelho.p256dh(), aparelho.auth());

		assertThat(aparelho.decifrar(corpo)).isEqualTo("{\"titulo\":\"Reserva confirmada\"}");
		// Cada envio usa sal e chave novos: a mesma mensagem nunca sai igual.
		assertThat(CriptografiaWebPush.cifrar("x".getBytes(), aparelho.p256dh(), aparelho.auth()))
				.isNotEqualTo(CriptografiaWebPush.cifrar("x".getBytes(), aparelho.p256dh(), aparelho.auth()));
	}

	@Test
	void autorizacaoVapidEUmJwtEs256ValidoParaAOrigemDoServico() throws Exception {
		KeyPair vapid = CriptografiaWebPush.novoParDeChaves();
		String publica = CriptografiaWebPush.chavePublicaEmBase64((ECPublicKey) vapid.getPublic());
		String cabecalho = CriptografiaWebPush.autorizacao("https://fcm.googleapis.com/fcm/send/abc", vapid.getPrivate(), publica,
				"mailto:suporte@condomix.com.br", Instant.parse("2026-11-20T12:00:00Z"));

		assertThat(cabecalho).startsWith("vapid t=").endsWith(", k=" + publica);
		String jwt = cabecalho.substring("vapid t=".length(), cabecalho.indexOf(','));
		String[] partes = jwt.split("\\.");
		String corpo = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
		assertThat(corpo).contains("\"aud\":\"https://fcm.googleapis.com\"").contains("\"exp\":1795176000")
				.contains("\"sub\":\"mailto:suporte@condomix.com.br\"");
		Signature verificacao = Signature.getInstance("SHA256withECDSAinP1363Format");
		verificacao.initVerify(vapid.getPublic());
		verificacao.update((partes[0] + "." + partes[1]).getBytes(StandardCharsets.US_ASCII));
		assertThat(verificacao.verify(Base64.getUrlDecoder().decode(partes[2]))).isTrue();
		assertThat(Base64.getUrlDecoder().decode(publica)).hasSize(65).startsWith((byte) 4);
	}

}
