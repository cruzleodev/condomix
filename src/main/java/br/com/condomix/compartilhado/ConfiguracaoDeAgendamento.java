package br.com.condomix.compartilhado;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Liga as tarefas agendadas ({@code @Scheduled}), como o processador de boletos. */
@Configuration
@EnableScheduling
class ConfiguracaoDeAgendamento {
}
