package br.com.condomix.acesso;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PessoaRepository extends JpaRepository<Pessoa, UUID> {

	List<Pessoa> findByTenantIdOrderByNome(UUID tenantId);

}
