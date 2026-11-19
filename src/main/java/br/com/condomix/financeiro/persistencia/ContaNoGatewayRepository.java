package br.com.condomix.financeiro.persistencia;

import br.com.condomix.financeiro.dominio.ContaNoGateway;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ContaNoGatewayRepository extends JpaRepository<ContaNoGateway, UUID> {

	/** A do condomínio atual (uma por condomínio). */
	@Query("select c from ContaNoGateway c")
	Optional<ContaNoGateway> doCondominio();

}
