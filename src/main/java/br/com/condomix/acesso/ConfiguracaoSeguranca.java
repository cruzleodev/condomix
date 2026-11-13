package br.com.condomix.acesso;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Regras de segurança da API.
 *
 * <ul>
 *   <li>Sem sessão no servidor: cada requisição traz o token no cabeçalho {@code Authorization: Bearer ...}.</li>
 *   <li>Abertos: login, health, info e a documentação (Swagger).</li>
 *   <li>{@code /api/auth/**} e {@code /api/eu}: basta estar logado.</li>
 *   <li>Todo o resto: precisa estar logado <b>e</b> com um condomínio selecionado no token.</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(PropriedadesJwt.class)
class ConfiguracaoSeguranca {

	static final String CLAIM_CONDOMINIO = "condominio";

	static final String CLAIM_PERFIS = "perfis";

	static final String CLAIM_EMAIL = "email";

	@Bean
	SecurityFilterChain seguranca(HttpSecurity http) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(regras -> regras
						.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
						.requestMatchers("/actuator/health/**", "/actuator/info", "/error",
								"/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
						.requestMatchers("/api/auth/**", "/api/eu").authenticated()
						.anyRequest().access(comCondominioSelecionado()))
				.oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(conversorDeToken())))
				.addFilterAfter(new FiltroDeTenant(), BearerTokenAuthenticationFilter.class)
				.build();
	}

	/** Libera só tokens que têm o condomínio definido. */
	private static AuthorizationManager<RequestAuthorizationContext> comCondominioSelecionado() {
		return (autenticacao, contexto) -> new AuthorizationDecision(
				autenticacao.get() instanceof JwtAuthenticationToken token
						&& token.getToken().hasClaim(CLAIM_CONDOMINIO));
	}

	/** Transforma os perfis do token em papéis do Spring Security: SINDICO vira ROLE_SINDICO. */
	private static JwtAuthenticationConverter conversorDeToken() {
		var perfis = new JwtGrantedAuthoritiesConverter();
		perfis.setAuthoritiesClaimName(CLAIM_PERFIS);
		perfis.setAuthorityPrefix("ROLE_");
		var conversor = new JwtAuthenticationConverter();
		conversor.setJwtGrantedAuthoritiesConverter(perfis);
		return conversor;
	}

	@Bean
	SecretKey chaveJwt(PropriedadesJwt propriedades) {
		return new SecretKeySpec(propriedades.segredo().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey chaveJwt) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(chaveJwt));
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey chaveJwt) {
		return NimbusJwtDecoder.withSecretKey(chaveJwt).macAlgorithm(MacAlgorithm.HS256).build();
	}

	/** Grava as senhas com BCrypt, no formato "{bcrypt}$2a$...". */
	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

}
