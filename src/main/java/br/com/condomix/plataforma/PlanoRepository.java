package br.com.condomix.plataforma;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlanoRepository extends JpaRepository<Plano, UUID> {

	Optional<Plano> findByCodigo(String codigo);

}
