package br.com.condomix.acesso;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Gera os tokens JWT. Conteúdo do token:
 * <ul>
 *   <li>{@code sub}: id do usuário</li>
 *   <li>{@code email}</li>
 *   <li>{@code condominio}: id do condomínio selecionado (ausente se nenhum foi escolhido ainda)</li>
 *   <li>{@code perfis}: perfis do usuário nesse condomínio</li>
 * </ul>
 */
@Component
class EmissorDeToken {

	private final JwtEncoder encoder;

	private final PropriedadesJwt propriedades;

	private final Clock relogio;

	EmissorDeToken(JwtEncoder encoder, PropriedadesJwt propriedades, Clock relogio) {
		this.encoder = encoder;
		this.propriedades = propriedades;
		this.relogio = relogio;
	}

	record TokenEmitido(String valor, Instant expiraEm) {
	}

	TokenEmitido emitir(Usuario usuario, UUID condominioId, Set<Perfil> perfis) {
		Instant agora = Instant.now(relogio);
		Instant expiraEm = agora.plus(propriedades.validade());
		var claims = JwtClaimsSet.builder()
				.issuer(propriedades.emissor())
				.subject(usuario.getId().toString())
				.issuedAt(agora)
				.expiresAt(expiraEm)
				.claim(ConfiguracaoSeguranca.CLAIM_EMAIL, usuario.getEmail())
				.claim(ConfiguracaoSeguranca.CLAIM_PERFIS, perfis.stream().map(Enum::name).sorted().toList());
		if (condominioId != null) {
			claims.claim(ConfiguracaoSeguranca.CLAIM_CONDOMINIO, condominioId.toString());
		}
		var cabecalho = JwsHeader.with(MacAlgorithm.HS256).build();
		String valor = encoder.encode(JwtEncoderParameters.from(cabecalho, claims.build())).getTokenValue();
		return new TokenEmitido(valor, expiraEm);
	}

}
