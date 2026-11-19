package br.com.condomix.financeiro.aplicacao;

import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.financeiro.CobrancaVenceEmBreve;
import br.com.condomix.financeiro.persistencia.CobrancaRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Todo dia de manhã: avisa das cobranças em aberto que vencem daqui a {@value #DIAS_ANTES} dias. Rodar de novo no
 * mesmo dia não repete o aviso (a comunicação ignora o acontecimento repetido).
 */
@Component
public class LembretesDeCobranca {

	public static final int DIAS_ANTES = 3;

	private final CobrancaRepository cobrancas;

	private final ApplicationEventPublisher eventos;

	private final TransactionTemplate transacao;

	private final Clock relogio;

	public LembretesDeCobranca(CobrancaRepository cobrancas, ApplicationEventPublisher eventos, TransactionTemplate transacao,
			Clock relogio) {
		this.cobrancas = cobrancas;
		this.eventos = eventos;
		this.transacao = transacao;
		this.relogio = relogio;
	}

	@Scheduled(cron = "${condomix.lembretes.horario:0 0 8 * * *}", zone = "${condomix.fuso-horario:America/Sao_Paulo}")
	public void lembrar() {
		LocalDate vencimento = LocalDate.now(relogio).plusDays(DIAS_ANTES);
		List<UUID> condominios = TenantAtual.comoSistema(() -> transacao.execute(s -> cobrancas.condominiosComCobrancaVencendoEm(vencimento)));
		for (UUID condominio : condominios) {
			TenantAtual.executarComo(condominio, () -> {
				transacao.executeWithoutResult(s -> cobrancas.abertasVencendoEm(vencimento)
						.forEach(c -> eventos.publishEvent(new CobrancaVenceEmBreve(c.getTenantId(), c.getId(), c.getUnidadeId(),
								c.getDescricao(), c.getValor(), c.getVencimento()))));
				return null;
			});
		}
	}

}
