package br.com.condomix.comunicacao.ouvintes;

import br.com.condomix.acesso.ContaExcluida;
import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.comunicacao.persistencia.InscricaoPushRepository;
import br.com.condomix.comunicacao.persistencia.NotificacaoRepository;
import br.com.condomix.comunicacao.persistencia.PreferenciaDeNotificacaoRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/** A pessoa excluiu a conta: apaga as notificações dela (em cada condomínio), os aparelhos do push e as preferências. */
@Component
class ExclusaoDosDadosDaConta {

	private final NotificacaoRepository notificacoes;

	private final InscricaoPushRepository inscricoes;

	private final PreferenciaDeNotificacaoRepository preferencias;

	private final TransactionTemplate transacao;

	ExclusaoDosDadosDaConta(NotificacaoRepository notificacoes, InscricaoPushRepository inscricoes,
			PreferenciaDeNotificacaoRepository preferencias, TransactionTemplate transacao) {
		this.notificacoes = notificacoes;
		this.inscricoes = inscricoes;
		this.preferencias = preferencias;
		this.transacao = transacao;
	}

	@Async
	@TransactionalEventListener
	void contaExcluida(ContaExcluida e) {
		transacao.executeWithoutResult(s -> {
			inscricoes.apagarDoUsuario(e.usuarioId());
			preferencias.apagarDoUsuario(e.usuarioId());
		});
		for (UUID condominio : e.condominios()) {
			TenantAtual.executarComo(condominio, () -> transacao.execute(s -> notificacoes.apagarDoUsuario(e.usuarioId())));
		}
	}

}
