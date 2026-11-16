package br.com.condomix.financeiro.persistencia;

import br.com.condomix.financeiro.dominio.GrupoDeUnidades;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GrupoDeUnidadesRepository extends JpaRepository<GrupoDeUnidades, UUID> {
}
