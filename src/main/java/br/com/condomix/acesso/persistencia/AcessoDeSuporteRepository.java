package br.com.condomix.acesso.persistencia;

import br.com.condomix.acesso.suporte.AcessoDeSuporte;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AcessoDeSuporteRepository extends JpaRepository<AcessoDeSuporte, UUID> {

	List<AcessoDeSuporte> findTop200ByOrderByInicioDesc();

	boolean existsByIdAndEncerradoEmIsNull(UUID id);

}
