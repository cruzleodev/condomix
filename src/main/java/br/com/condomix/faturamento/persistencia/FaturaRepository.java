package br.com.condomix.faturamento.persistencia;

import br.com.condomix.faturamento.SituacaoDaFatura;
import br.com.condomix.faturamento.dominio.Fatura;
import br.com.condomix.pagamentos.TipoDeGateway;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Dentro de um condomínio, o Hibernate filtra pelo tenant. Em modo sistema (rotina, processador e painel da
 * plataforma), as consultas veem as faturas de todos.
 */
public interface FaturaRepository extends JpaRepository<Fatura, UUID> {

	List<Fatura> findAllByOrderByPeriodoInicioDescCriadoEmDesc();

	/** Onde terminou o último período faturado, inclusive o de faturas canceladas (o período foi perdoado). */
	@Query("select max(f.periodoFim) from Fatura f")
	Optional<LocalDate> fimDaUltima();

	List<Fatura> findBySituacaoOrderByCriadoEm(SituacaoDaFatura situacao, Limit limite);

	Optional<Fatura> findByGatewayAndIdNoGateway(TipoDeGateway gateway, String idNoGateway);

	@Query("""
			select distinct f.tenantId from Fatura f
			where f.situacao in (br.com.condomix.faturamento.SituacaoDaFatura.A_EMITIR,
			                     br.com.condomix.faturamento.SituacaoDaFatura.A_CANCELAR)
			""")
	List<UUID> condominiosComFaturasAProcessar();

	/** Condomínios com fatura emitida que venceu antes de {@code limite} (passou da tolerância). */
	@Query("""
			select distinct f.tenantId from Fatura f
			where f.situacao = br.com.condomix.faturamento.SituacaoDaFatura.EMITIDA and f.vencimento < :limite
			""")
	List<UUID> condominiosComFaturaVencidaAntesDe(LocalDate limite);

	boolean existsBySituacaoAndVencimentoBefore(SituacaoDaFatura situacao, LocalDate limite);

}
