package br.com.condomix.comunicacao.persistencia;

import br.com.condomix.comunicacao.dominio.Notificacao;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Dentro do condomínio atual (o Hibernate filtra pelo tenant). */
public interface NotificacaoRepository extends JpaRepository<Notificacao, UUID> {

	List<Notificacao> findByUsuarioIdOrderByCriadoEmDesc(UUID usuarioId, Limit limite);

	long countByUsuarioIdAndLidoEmIsNull(UUID usuarioId);

	/** Sem filtro de tenant de propósito: a mesma pessoa não recebe o mesmo acontecimento duas vezes. */
	boolean existsByUsuarioIdAndChave(UUID usuarioId, String chave);

	@Modifying
	@Query("update Notificacao n set n.lidoEm = :quando where n.usuarioId = :usuarioId and n.lidoEm is null")
	int marcarTodasLidas(UUID usuarioId, Instant quando);

	/** As do condomínio atual (a entrega some junto, pelo ON DELETE CASCADE). */
	@Modifying
	@Query("delete from Notificacao n where n.usuarioId = :usuarioId")
	int apagarDoUsuario(UUID usuarioId);

}
