package br.com.condomix.comunicacao.aplicacao;

import br.com.condomix.acesso.DestinatariosDeNotificacoes;
import br.com.condomix.acesso.DestinatariosDeNotificacoes.Destinatario;
import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.comunicacao.canais.CanalDeNotificacao;
import br.com.condomix.comunicacao.canais.ConteudoDaNotificacao;
import br.com.condomix.comunicacao.canais.FalhaNaEntrega;
import br.com.condomix.comunicacao.dominio.Canal;
import br.com.condomix.comunicacao.dominio.EntregaDeNotificacao;
import br.com.condomix.comunicacao.dominio.Notificacao;
import br.com.condomix.comunicacao.persistencia.EntregaDeNotificacaoRepository;
import br.com.condomix.comunicacao.persistencia.NotificacaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Entrega por e-mail e push as notificações pendentes, de tempos em tempos. A chamada ao provedor fica fora da
 * transação do banco. Se falhar, a entrega tenta de novo mais tarde (1 min, 5 min, 30 min, 2 h) e depois desiste.
 */
@Component
@EnableConfigurationProperties(PropriedadesDasNotificacoes.class)
public class EntregaDeNotificacoes {

	private static final Logger log = LoggerFactory.getLogger(EntregaDeNotificacoes.class);

	private static final int LOTE = 100;

	private final EntregaDeNotificacaoRepository entregas;

	private final NotificacaoRepository notificacoes;

	private final DestinatariosDeNotificacoes destinatarios;

	private final Map<Canal, CanalDeNotificacao> canais = new EnumMap<>(Canal.class);

	private final TransactionTemplate transacao;

	private final Clock relogio;

	private final PropriedadesDasNotificacoes propriedades;

	public EntregaDeNotificacoes(EntregaDeNotificacaoRepository entregas, NotificacaoRepository notificacoes,
			DestinatariosDeNotificacoes destinatarios, List<CanalDeNotificacao> canais, TransactionTemplate transacao,
			Clock relogio, PropriedadesDasNotificacoes propriedades) {
		this.entregas = entregas;
		this.notificacoes = notificacoes;
		this.destinatarios = destinatarios;
		canais.forEach(c -> this.canais.put(c.canal(), c));
		this.transacao = transacao;
		this.relogio = relogio;
		this.propriedades = propriedades;
	}

	@Scheduled(fixedDelayString = "${condomix.notificacoes.intervalo-da-entrega:15s}", initialDelayString = "20s")
	void agendada() {
		if (propriedades.automatico()) {
			entregarPendentes();
		}
	}

	public void entregarPendentes() {
		Instant agora = Instant.now(relogio);
		List<UUID> condominios = TenantAtual.comoSistema(() -> transacao.execute(s -> entregas.condominiosComEntregasPendentes(agora)));
		for (UUID condominio : condominios) {
			try {
				TenantAtual.executarComo(condominio, () -> {
					transacao.execute(s -> entregas.pendentes(agora, Limit.of(LOTE)).stream().map(EntregaDeNotificacao::getId).toList())
							.forEach(this::entregar);
					return null;
				});
			}
			catch (RuntimeException e) {
				log.warn("Falha ao entregar as notificações do condomínio {}", condominio, e);
			}
		}
	}

	private record PreparoDaEntrega(ConteudoDaNotificacao conteudo, Destinatario destinatario, Canal canal) {
	}

	private void entregar(UUID entregaId) {
		PreparoDaEntrega preparo = transacao.execute(s -> {
			EntregaDeNotificacao entrega = entregas.findById(entregaId).orElseThrow();
			Notificacao n = notificacoes.findById(entrega.getNotificacaoId()).orElseThrow();
			Destinatario quem = destinatarios.usuario(n.getUsuarioId()).orElse(null);
			return quem == null ? null : new PreparoDaEntrega(new ConteudoDaNotificacao(n.getId(), n.getGrupo(), n.getTitulo(), n.getTexto(),
					n.getLink()), quem, entrega.getCanal());
		});
		if (preparo == null) {
			gravar(entregaId, e -> e.descartada("O login não está mais ativo"));
			return;
		}
		CanalDeNotificacao canal = canais.get(preparo.canal());
		try {
			boolean entregue = canal.entregar(preparo.conteudo(), preparo.destinatario());
			gravar(entregaId, e -> {
				if (entregue) {
					e.enviada(Instant.now(relogio));
				}
				else {
					e.descartada("Nenhum aparelho com o push ligado");
				}
			});
		}
		catch (FalhaNaEntrega e) {
			gravar(entregaId, x -> x.falhou(e.getMessage(), Instant.now(relogio)));
		}
		catch (RuntimeException e) {
			log.warn("Erro inesperado ao entregar a notificação {}", entregaId, e);
			gravar(entregaId, x -> x.falhou("Erro inesperado: " + e.getMessage(), Instant.now(relogio)));
		}
	}

	private void gravar(UUID entregaId, Consumer<EntregaDeNotificacao> mudanca) {
		transacao.executeWithoutResult(s -> entregas.findById(entregaId).ifPresent(mudanca));
	}

}
