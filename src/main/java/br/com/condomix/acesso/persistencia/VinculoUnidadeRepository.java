package br.com.condomix.acesso.persistencia;

import br.com.condomix.acesso.VinculoUnidade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface VinculoUnidadeRepository extends JpaRepository<VinculoUnidade, UUID> {

	List<VinculoUnidade> findByUnidadeId(UUID unidadeId);

	List<VinculoUnidade> findByPessoaId(UUID pessoaId);

	/** Vínculos ativos na data, já com a unidade e a pessoa carregadas. */
	@Query("""
			select v from VinculoUnidade v join fetch v.unidade join fetch v.pessoa
			where v.inicio <= :data and (v.fim is null or v.fim >= :data)
			""")
	List<VinculoUnidade> vigentes(@Param("data") LocalDate data);

}
