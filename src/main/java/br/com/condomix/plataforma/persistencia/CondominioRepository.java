package br.com.condomix.plataforma.persistencia;

import br.com.condomix.plataforma.Condominio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CondominioRepository extends JpaRepository<Condominio, UUID> {
}
