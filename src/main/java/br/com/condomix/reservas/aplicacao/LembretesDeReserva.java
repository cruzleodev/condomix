package br.com.condomix.reservas.aplicacao;

import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.reservas.ReservaAmanha;
import br.com.condomix.reservas.dominio.Espaco;
import br.com.condomix.reservas.dominio.SituacaoDaReserva;
import br.com.condomix.reservas.persistencia.EspacoRepository;
import br.com.condomix.reservas.persistencia.ReservaRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Todo dia de manhã: lembra quem tem reserva amanhã. Rodar de novo no mesmo dia não repete o aviso. */
@Component
public class LembretesDeReserva {

	private final ReservaRepository reservas;

	private final EspacoRepository espacos;

	private final ApplicationEventPublisher eventos;

	private final TransactionTemplate transacao;

	private final Clock relogio;

	public LembretesDeReserva(ReservaRepository reservas, EspacoRepository espacos, ApplicationEventPublisher eventos,
			TransactionTemplate transacao, Clock relogio) {
		this.reservas = reservas;
		this.espacos = espacos;
		this.eventos = eventos;
		this.transacao = transacao;
		this.relogio = relogio;
	}

	@Scheduled(cron = "${condomix.lembretes.horario:0 0 8 * * *}", zone = "${condomix.fuso-horario:America/Sao_Paulo}")
	public void lembrar() {
		LocalDate amanha = LocalDate.now(relogio).plusDays(1);
		List<UUID> condominios = TenantAtual.comoSistema(() -> transacao.execute(s -> reservas.condominiosComReservaEm(amanha)));
		for (UUID condominio : condominios) {
			TenantAtual.executarComo(condominio, () -> {
				transacao.executeWithoutResult(s -> {
					Map<UUID, String> nomes = espacos.findAll().stream().collect(Collectors.toMap(Espaco::getId, Espaco::getNome));
					reservas.findByDataBetweenOrderByDataAscInicioAsc(amanha, amanha).stream()
							.filter(r -> r.getSituacao() == SituacaoDaReserva.CONFIRMADA)
							.forEach(r -> eventos.publishEvent(new ReservaAmanha(r.getTenantId(), r.getId(), r.getUnidadeId(),
									nomes.getOrDefault(r.getEspacoId(), "Espaço"), r.getData(), r.getPeriodo(), r.getInicio(), r.getFim())));
				});
				return null;
			});
		}
	}

}
