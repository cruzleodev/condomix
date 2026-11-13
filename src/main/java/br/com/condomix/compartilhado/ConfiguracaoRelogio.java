package br.com.condomix.compartilhado;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * O relógio do sistema, injetável (princípio D do SOLID). Toda regra que depende de data ou hora
 * recebe um {@link Clock} em vez de chamar {@code Instant.now()}; nos testes, usa-se um relógio fixo.
 */
@Configuration
class ConfiguracaoRelogio {

	@Bean
	Clock relogio(@Value("${condomix.fuso-horario:America/Sao_Paulo}") String fusoHorario) {
		return Clock.system(ZoneId.of(fusoHorario));
	}

}
