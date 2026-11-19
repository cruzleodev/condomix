package br.com.condomix.comunicacao.persistencia;

import br.com.condomix.comunicacao.dominio.EntregaDeNotificacao;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EntregaDeNotificacaoRepository extends JpaRepository<EntregaDeNotificacao, UUID> {

	/** Em modo sistema: os condomínios com entregas na hora de tentar. */
	@Query("""
			select distinct e.tenantId from EntregaDeNotificacao e
			where e.situacao = br.com.condomix.comunicacao.dominio.SituacaoDaEntrega.PENDENTE and e.proximaTentativaEm <= :agora
			""")
	List<UUID> condominiosComEntregasPendentes(Instant agora);

	@Query("""
			select e from EntregaDeNotificacao e
			where e.situacao = br.com.condomix.comunicacao.dominio.SituacaoDaEntrega.PENDENTE and e.proximaTentativaEm <= :agora
			order by e.proximaTentativaEm
			""")
	List<EntregaDeNotificacao> pendentes(Instant agora, Limit limite);

	List<EntregaDeNotificacao> findByNotificacaoId(UUID notificacaoId);

}
