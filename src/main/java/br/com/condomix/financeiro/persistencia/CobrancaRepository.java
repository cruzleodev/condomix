package br.com.condomix.financeiro.persistencia;

import br.com.condomix.financeiro.dominio.Cobranca;
import br.com.condomix.financeiro.dominio.OrigemCobranca;
import br.com.condomix.financeiro.dominio.StatusCobranca;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

public interface CobrancaRepository extends JpaRepository<Cobranca, UUID>, JpaSpecificationExecutor<Cobranca> {

	/** Filtros opcionais (vazio = todos). Montados só com o que foi informado. */
	static Specification<Cobranca> filtros(LocalDate competencia, StatusCobranca status, UUID unidadeId) {
		return (raiz, consulta, cb) -> cb.and(Stream.of(
						competencia == null ? null : cb.equal(raiz.get("competencia"), competencia),
						status == null ? null : cb.equal(raiz.get("status"), status),
						unidadeId == null ? null : cb.equal(raiz.get("unidadeId"), unidadeId))
				.filter(Objects::nonNull)
				.toArray(Predicate[]::new));
	}

	List<Cobranca> findByGeracaoId(UUID geracaoId);

	/** Em aberto e emitidas pelo condomínio (as importadas já têm o boleto da administradora). */
	List<Cobranca> findByStatusAndOrigemNot(StatusCobranca status, OrigemCobranca origem);

	@Query("select c from Cobranca c where c.status = br.com.condomix.financeiro.dominio.StatusCobranca.ABERTA and c.vencimento < :data")
	List<Cobranca> vencidasEm(@Param("data") LocalDate data);

	@Query("""
			select c from Cobranca c
			where c.unidadeId in :unidades
			  and (c.status = br.com.condomix.financeiro.dominio.StatusCobranca.ABERTA or c.vencimento >= :desde)
			  and c.status <> br.com.condomix.financeiro.dominio.StatusCobranca.CANCELADA
			order by c.vencimento desc
			""")
	List<Cobranca> daUnidades(@Param("unidades") Collection<UUID> unidades, @Param("desde") LocalDate desde);

	@Query("""
			select count(c) > 0 from Cobranca c
			where c.unidadeId = :unidadeId and c.competencia = :competencia
			  and c.status <> br.com.condomix.financeiro.dominio.StatusCobranca.CANCELADA
			""")
	boolean existeNaCompetencia(@Param("unidadeId") UUID unidadeId, @Param("competencia") LocalDate competencia);

	/** Em aberto e vencida antes de {@code data} (inadimplência da unidade). */
	boolean existsByUnidadeIdAndStatusAndVencimentoBefore(UUID unidadeId, StatusCobranca status, LocalDate data);

	/** Em aberto, emitidas pelo condomínio, vencendo na data (lembrete do dia). */
	@Query("""
			select c from Cobranca c
			where c.status = br.com.condomix.financeiro.dominio.StatusCobranca.ABERTA
			  and c.origem <> br.com.condomix.financeiro.dominio.OrigemCobranca.IMPORTACAO
			  and c.vencimento = :data
			""")
	List<Cobranca> abertasVencendoEm(@Param("data") LocalDate data);

	/** Em modo sistema: os condomínios com cobrança em aberto vencendo na data. */
	@Query("""
			select distinct c.tenantId from Cobranca c
			where c.status = br.com.condomix.financeiro.dominio.StatusCobranca.ABERTA and c.vencimento = :data
			""")
	List<UUID> condominiosComCobrancaVencendoEm(@Param("data") LocalDate data);

}
