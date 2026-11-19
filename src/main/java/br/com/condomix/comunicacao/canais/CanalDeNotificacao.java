package br.com.condomix.comunicacao.canais;

import br.com.condomix.acesso.DestinatariosDeNotificacoes.Destinatario;
import br.com.condomix.comunicacao.dominio.Canal;

/**
 * Um jeito de entregar a notificação fora do app (SOLID: O). Hoje: e-mail e Web Push; o WhatsApp entra como
 * mais uma implementação. Todas passam no {@code CanalDeNotificacaoContrato}.
 */
public interface CanalDeNotificacao {

	Canal canal();

	/** Falso quando o canal não está configurado nesta instalação: as entregas nem são criadas. */
	boolean disponivel();

	/**
	 * Entrega agora. Lança {@link FalhaNaEntrega} quando vale tentar de novo mais tarde.
	 *
	 * @return {@code false} quando não havia para onde entregar (ex.: nenhum aparelho com push): não é falha
	 */
	boolean entregar(ConteudoDaNotificacao conteudo, Destinatario destinatario);

}
