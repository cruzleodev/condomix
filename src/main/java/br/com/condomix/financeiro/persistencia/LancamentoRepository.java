package br.com.condomix.financeiro.persistencia;

import br.com.condomix.financeiro.dominio.Lancamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LancamentoRepository extends JpaRepository<Lancamento, UUID> {

	/** Lançamentos válidos (não cancelados) até a data, com conta e categoria. */
	@Query("""
			select l from Lancamento l join fetch l.conta left join fetch l.categoria
			where l.canceladoEm is null and l.data <= :ate
			""")
	List<Lancamento> validosAte(@Param("ate") LocalDate ate);

	/** Extrato: todos os lançamentos do período (inclusive cancelados, para auditoria), mais recentes primeiro. */
	@Query("""
			select l from Lancamento l join fetch l.conta left join fetch l.categoria
			where l.data between :de and :ate
			order by l.data desc, l.criadoEm desc
			""")
	List<Lancamento> doPeriodo(@Param("de") LocalDate de, @Param("ate") LocalDate ate);

	List<Lancamento> findByCobrancaIdAndCanceladoEmIsNull(UUID cobrancaId);

	List<Lancamento> findByTransferenciaId(UUID transferenciaId);

}
