package br.com.condomix.acesso.persistencia;

import br.com.condomix.acesso.Unidade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface UnidadeRepository extends JpaRepository<Unidade, UUID> {

	List<Unidade> findByTenantIdOrderByBlocoAscIdentificacaoAsc(UUID tenantId);

	@Query("select u from Unidade u order by u.bloco nulls first, u.identificacao")
	List<Unidade> todasEmOrdem();

}
