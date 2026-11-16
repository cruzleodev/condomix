package br.com.condomix.financeiro.persistencia;

import br.com.condomix.financeiro.dominio.MembroDeGrupo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MembroDeGrupoRepository extends JpaRepository<MembroDeGrupo, UUID> {

	List<MembroDeGrupo> findByGrupoId(UUID grupoId);

	void deleteByGrupoId(UUID grupoId);

	void deleteByUnidadeIdIn(Collection<UUID> unidades);

}
