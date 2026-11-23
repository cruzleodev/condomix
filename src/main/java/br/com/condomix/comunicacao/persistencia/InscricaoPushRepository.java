package br.com.condomix.comunicacao.persistencia;

import br.com.condomix.comunicacao.dominio.InscricaoPush;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InscricaoPushRepository extends JpaRepository<InscricaoPush, UUID> {

	List<InscricaoPush> findByUsuarioId(UUID usuarioId);

	Optional<InscricaoPush> findByEndpoint(String endpoint);

	boolean existsByUsuarioId(UUID usuarioId);

	@org.springframework.data.jpa.repository.Modifying
	@org.springframework.data.jpa.repository.Query("delete from InscricaoPush i where i.usuarioId = :usuarioId")
	int apagarDoUsuario(UUID usuarioId);

}
