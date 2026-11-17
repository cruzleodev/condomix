package br.com.condomix;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O OpenAPI usa o nome simples do record como nome do tipo. Dois records com o mesmo nome (ex.: dois
 * "CategoriaResposta" em módulos diferentes) se sobrescrevem e a documentação da API fica errada, e com ela
 * os tipos do frontend. Este teste impede nomes repetidos.
 */
class ContratoDaApiTests {

	@Test
	void recordsTemNomesUnicos() {
		var classes = new ClassFileImporter()
				.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPackages("br.com.condomix");
		Map<String, List<String>> porNome = classes.stream()
				.filter(JavaClass::isRecord)
				.collect(Collectors.groupingBy(JavaClass::getSimpleName,
						Collectors.mapping(JavaClass::getName, Collectors.toList())));
		var repetidos = porNome.entrySet().stream().filter(e -> e.getValue().size() > 1).toList();

		assertThat(repetidos).as("records com o mesmo nome (renomeie para um nome único)").isEmpty();
	}

}
