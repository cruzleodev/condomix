package br.com.condomix.comunicacao.aplicacao;

import br.com.condomix.acesso.DestinatariosDeNotificacoes;
import br.com.condomix.acesso.DestinatariosDeNotificacoes.Destinatario;
import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.comunicacao.canais.CanalDeNotificacao;
import br.com.condomix.comunicacao.dominio.Canal;
import br.com.condomix.comunicacao.dominio.EntregaDeNotificacao;
import br.com.condomix.comunicacao.dominio.GrupoDeNotificacao;
import br.com.condomix.comunicacao.dominio.Notificacao;
import br.com.condomix.comunicacao.persistencia.EntregaDeNotificacaoRepository;
import br.com.condomix.comunicacao.persistencia.NotificacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Cria as notificações de um acontecimento: uma para cada destinatário, no sino do app, e as entregas por e-mail e
 * push que a pessoa quer receber. A entrega em si fica com a {@link EntregaDeNotificacoes}, que tenta de novo se falhar.
 * Sem {@code @Transactional} na classe: o condomínio é definido antes da transação.
 */
@Service
public class CentralDeNotificacoes {

	private final NotificacaoRepository notificacoes;

	private final EntregaDeNotificacaoRepository entregas;

	private final DestinatariosDeNotificacoes destinatarios;

	private final PreferenciasDeNotificacoes preferencias;

	private final List<CanalDeNotificacao> canais;

	private final TransactionTemplate transacao;

	private final Clock relogio;

	public CentralDeNotificacoes(NotificacaoRepository notificacoes, EntregaDeNotificacaoRepository entregas,
			DestinatariosDeNotificacoes destinatarios, PreferenciasDeNotificacoes preferencias, List<CanalDeNotificacao> canais,
			TransactionTemplate transacao, Clock relogio) {
		this.notificacoes = notificacoes;
		this.entregas = entregas;
		this.destinatarios = destinatarios;
		this.preferencias = preferencias;
		this.canais = canais;
		this.transacao = transacao;
		this.relogio = relogio;
	}

	/**
	 * O que avisar.
	 *
	 * @param chave identifica o acontecimento (ex.: "cobranca-paga:{id}"): repetir não notifica de novo
	 * @param link  a tela do app (ex.: "/debitos")
	 */
	public record NovaNotificacao(GrupoDeNotificacao grupo, String chave, String titulo, String texto, String link) {
	}

	/**
	 * Notifica, no condomínio, quem {@code quem} escolher (ex.: os pagadores de uma unidade).
	 *
	 * @return quantas notificações foram criadas (repetidas não contam)
	 */
	public int notificar(UUID condominioId, Function<DestinatariosDeNotificacoes, List<Destinatario>> quem, NovaNotificacao nova) {
		return TenantAtual.executarComo(condominioId, () -> transacao.execute(s -> {
			Instant agora = Instant.now(relogio);
			int criadas = 0;
			for (Destinatario destinatario : quem.apply(destinatarios)) {
				if (notificacoes.existsByUsuarioIdAndChave(destinatario.usuarioId(), nova.chave())) {
					continue;
				}
				Notificacao notificacao = notificacoes.save(new Notificacao(condominioId, destinatario.usuarioId(), nova.grupo(),
						nova.chave(), nova.titulo(), nova.texto(), nova.link()));
				Set<Canal> desligados = preferencias.desligados(destinatario.usuarioId(), nova.grupo());
				canais.stream()
						.filter(c -> c.disponivel() && !desligados.contains(c.canal()))
						.forEach(c -> entregas.save(new EntregaDeNotificacao(notificacao, c.canal(), agora)));
				criadas++;
			}
			return criadas;
		}));
	}

}
