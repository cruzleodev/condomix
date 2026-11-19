package br.com.condomix.acesso.persistencia;

import br.com.condomix.acesso.Perfil;
import br.com.condomix.acesso.PerfilAcesso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PerfilAcessoRepository extends JpaRepository<PerfilAcesso, UUID> {

	List<PerfilAcesso> findByUsuarioIdAndAtivoTrue(UUID usuarioId);

	boolean existsByUsuarioIdAndPerfil(UUID usuarioId, Perfil perfil);

	/** Perfis ativos do condomínio atual, com o login carregado (notificações). */
	@org.springframework.data.jpa.repository.Query("select p from PerfilAcesso p join fetch p.usuario where p.ativo = true")
	List<PerfilAcesso> ativosComUsuario();

}
