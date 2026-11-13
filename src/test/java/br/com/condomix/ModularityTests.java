package br.com.condomix;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Garante que os módulos (plataforma, acesso, documentos, financeiro, reservas, comunicacao)
 * não acessam as partes internas uns dos outros. Também gera a documentação dos módulos
 * em target/spring-modulith-docs.
 */
class ModularityTests {

	private final ApplicationModules modules = ApplicationModules.of(CondomixApplication.class);

	@Test
	void verificaEstruturaDosModulos() {
		modules.verify();
	}

	@Test
	void geraDocumentacaoDosModulos() {
		new Documenter(modules).writeDocumentation();
	}

}
