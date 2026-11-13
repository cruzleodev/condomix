package br.com.condomix.acesso;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PerfilAcessoRepository extends JpaRepository<PerfilAcesso, UUID> {

	List<PerfilAcesso> findByUsuarioIdAndAtivoTrue(UUID usuarioId);

}
