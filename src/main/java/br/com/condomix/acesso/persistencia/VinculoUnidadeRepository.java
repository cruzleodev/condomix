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

	/** Todos os vínculos da pessoa (atuais e encerrados), mais recentes primeiro. */
	@Query("select v from VinculoUnidade v join fetch v.unidade where v.pessoa.id = :pessoaId order by v.inicio desc")
	List<VinculoUnidade> historicoDaPessoa(@Param("pessoaId") UUID pessoaId);

	/** As unidades do usuário logado no condomínio atual ("meus dados"): pela pessoa ligada ao login. */
	@Query("""
			select v from VinculoUnidade v join fetch v.unidade join fetch v.pessoa p
			where p.usuarioId = :usuarioId and v.inicio <= :data and (v.fim is null or v.fim >= :data)
			""")
	List<VinculoUnidade> vigentesDoUsuario(@Param("usuarioId") UUID usuarioId, @Param("data") LocalDate data);

}
