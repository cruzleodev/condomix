package br.com.condomix.plataforma;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssinaturaRepository extends JpaRepository<Assinatura, UUID> {

	List<Assinatura> findByTenantId(UUID tenantId);

}
