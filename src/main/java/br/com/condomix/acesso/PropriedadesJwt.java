package br.com.condomix.acesso;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Configuração dos tokens JWT ({@code condomix.seguranca.jwt.*}).
 *
 * @param segredo  chave usada para assinar os tokens (HS256). Mínimo de 32 bytes.
 *                 Em produção vem da variável de ambiente {@code JWT_SEGREDO}.
 * @param validade por quanto tempo o token vale
 * @param emissor  valor do campo "iss" do token
 */
@ConfigurationProperties("condomix.seguranca.jwt")
record PropriedadesJwt(String segredo, Duration validade, String emissor) {

	PropriedadesJwt {
		if (segredo == null || segredo.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException(
					"condomix.seguranca.jwt.segredo precisa ter pelo menos 32 bytes (defina JWT_SEGREDO)");
		}
		if (validade == null) {
			validade = Duration.ofHours(12);
		}
		if (emissor == null) {
			emissor = "condomix";
		}
	}

}
