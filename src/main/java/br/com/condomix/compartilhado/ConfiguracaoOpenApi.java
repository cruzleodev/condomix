package br.com.condomix.compartilhado;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Documentação da API no Swagger (http://localhost:8080/swagger-ui/index.html).
 * Declara o token JWT, o que faz aparecer o botão "Authorize".
 */
@Configuration
@OpenAPIDefinition(
		info = @Info(title = "Condomix API", version = "v1",
				description = "Gestão de condomínios. Faça o login, copie o token e use o botão Authorize."),
		security = @SecurityRequirement(name = ConfiguracaoOpenApi.TOKEN))
@SecurityScheme(name = ConfiguracaoOpenApi.TOKEN, type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
class ConfiguracaoOpenApi {

	static final String TOKEN = "token";

}
