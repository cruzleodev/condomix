package br.com.condomix.comunicacao.aplicacao;

import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.comunicacao.dominio.Notificacao;
import br.com.condomix.comunicacao.persistencia.NotificacaoRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** O sino do app: as notificações de quem está logado, no condomínio atual. */
@Service
@Transactional
public class ConsultaDeNotificacoes {

	private static final int QUANTAS = 50;

	private final NotificacaoRepository notificacoes;

	private final Clock relogio;

	public ConsultaDeNotificacoes(NotificacaoRepository notificacoes, Clock relogio) {
		this.notificacoes = notificacoes;
		this.relogio = relogio;
	}

	@Transactional(readOnly = true)
	public List<Notificacao> recentes(UUID usuarioId) {
		return notificacoes.findByUsuarioIdOrderByCriadoEmDesc(usuarioId, Limit.of(QUANTAS));
	}

	@Transactional(readOnly = true)
	public long naoLidas(UUID usuarioId) {
		return notificacoes.countByUsuarioIdAndLidoEmIsNull(usuarioId);
	}

	public void marcarLida(UUID id, UUID usuarioId) {
		notificacoes.findById(id).filter(n -> n.getUsuarioId().equals(usuarioId))
				.orElseThrow(() -> new NaoEncontradoException("Notificação não encontrada"))
				.marcarLida(Instant.now(relogio));
	}

	public int marcarTodasLidas(UUID usuarioId) {
		return notificacoes.marcarTodasLidas(usuarioId, Instant.now(relogio));
	}

}
