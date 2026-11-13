package br.com.condomix.plataforma;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CondominioRepository extends JpaRepository<Condominio, UUID> {
}
