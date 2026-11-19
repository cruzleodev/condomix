package br.com.condomix.compartilhado;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga as tarefas de tempos em tempos ({@code @Scheduled}, ex.: o processador de boletos) e os ouvintes de eventos
 * em segundo plano ({@code @Async}, ex.: os avisos, que não podem atrasar quem gerou o evento).
 */
@Configuration
@EnableScheduling
@EnableAsync
class ConfiguracaoDeAgendamento {
}
