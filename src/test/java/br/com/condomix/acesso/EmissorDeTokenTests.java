package br.com.condomix.acesso;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Teste de unidade, sem Spring: o relógio fixo permite conferir a expiração exata do token. */
class EmissorDeTokenTests {

	private static final Instant AGORA = Instant.parse("2026-01-10T12:00:00Z");

	private final SecretKey chave = new SecretKeySpec(
			"chave-de-teste-com-pelo-menos-32-bytes!!".getBytes(StandardCharsets.UTF_8), "HmacSHA256");

	private final EmissorDeToken emissor = new EmissorDeToken(
			new NimbusJwtEncoder(new ImmutableSecret<>(chave)),
			new PropriedadesJwt("chave-de-teste-com-pelo-menos-32-bytes!!", Duration.ofHours(2), "condomix"),
			Clock.fixed(AGORA, ZoneOffset.UTC));

	@Test
	void tokenTemCondominioPerfisEExpiracaoCorreta() {
		Usuario usuario = usuarioComId("sindico@teste.com");
		UUID condominio = UUID.randomUUID();

		var emitido = emissor.emitir(usuario, condominio, Set.of(Perfil.SINDICO, Perfil.CONSELHO));
		Jwt jwt = decodificar(emitido.valor());

		assertThat(emitido.expiraEm()).isEqualTo(AGORA.plus(Duration.ofHours(2)));
		assertThat(jwt.getIssuedAt()).isEqualTo(AGORA);
		assertThat(jwt.getSubject()).isEqualTo(usuario.getId().toString());
		assertThat(jwt.getClaimAsString("condominio")).isEqualTo(condominio.toString());
		assertThat(jwt.getClaimAsStringList("perfis")).containsExactly("CONSELHO", "SINDICO");
	}

	@Test
	void semCondominioOTokenNaoTemOCampo() {
		Jwt jwt = decodificar(emissor.emitir(usuarioComId("x@teste.com"), null, Set.of()).valor());

		assertThat(jwt.hasClaim("condominio")).isFalse();
	}

	/** Decodifica sem validar a expiração, porque o relógio do teste está no passado. */
	private Jwt decodificar(String token) {
		var decoder = NimbusJwtDecoder.withSecretKey(chave).macAlgorithm(MacAlgorithm.HS256).build();
		decoder.setJwtValidator(jwt -> org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success());
		return decoder.decode(token);
	}

	private static Usuario usuarioComId(String email) {
		Usuario usuario = new Usuario(email, "{noop}x");
		ReflectionTestUtils.setField(usuario, "id", UUID.randomUUID());
		return usuario;
	}

}
