package br.com.condomix.acesso.persistencia;

import br.com.condomix.acesso.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

	/** O e-mail é gravado sempre em minúsculas (ver {@link Usuario#normalizarEmail}). */
	Optional<Usuario> findByEmail(String email);

	boolean existsByAdminPlataformaTrue();

	boolean existsByIdAndAtivoTrue(UUID id);

	/** Busca do painel da plataforma: parte do e-mail (já em minúsculas). */
	List<Usuario> findTop20ByEmailContainingOrderByEmail(String parteDoEmail);

	default Optional<Usuario> buscarPorEmail(String email) {
		return findByEmail(Usuario.normalizarEmail(email));
	}

}
