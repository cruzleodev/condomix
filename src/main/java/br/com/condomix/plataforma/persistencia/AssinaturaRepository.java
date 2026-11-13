package br.com.condomix.plataforma.persistencia;

import br.com.condomix.plataforma.Assinatura;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssinaturaRepository extends JpaRepository<Assinatura, UUID> {

	List<Assinatura> findByTenantId(UUID tenantId);

}
