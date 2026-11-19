package br.com.condomix.comunicacao.ouvintes;

import br.com.condomix.comunicacao.aplicacao.CentralDeNotificacoes;
import br.com.condomix.comunicacao.aplicacao.CentralDeNotificacoes.NovaNotificacao;
import br.com.condomix.comunicacao.dominio.GrupoDeNotificacao;
import br.com.condomix.documentos.DocumentoPublicado;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Documento novo: avisa todos do condomínio. */
@Component
class NotificacoesDosDocumentos {

	private final CentralDeNotificacoes central;

	NotificacoesDosDocumentos(CentralDeNotificacoes central) {
		this.central = central;
	}

	@Async
	@TransactionalEventListener
	void documentoPublicado(DocumentoPublicado e) {
		central.notificar(e.tenantId(), d -> d.doCondominio(), new NovaNotificacao(GrupoDeNotificacao.DOCUMENTOS,
				"documento:" + e.documentoId(), "Documento novo: " + e.titulo(),
				e.categoria().rotulo() + " publicado no app do condomínio.", "/documentos"));
	}

}
