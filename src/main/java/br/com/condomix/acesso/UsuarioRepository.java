package br.com.condomix.acesso;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

	/** O e-mail é gravado sempre em minúsculas (ver {@link Usuario}). */
	Optional<Usuario> findByEmail(String email);

	default Optional<Usuario> buscarPorEmail(String email) {
		return findByEmail(Usuario.normalizar(email));
	}

}
