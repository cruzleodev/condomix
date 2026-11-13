package br.com.condomix.acesso.persistencia;

import br.com.condomix.acesso.Unidade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UnidadeRepository extends JpaRepository<Unidade, UUID> {

	List<Unidade> findByTenantIdOrderByBlocoAscIdentificacaoAsc(UUID tenantId);

}
