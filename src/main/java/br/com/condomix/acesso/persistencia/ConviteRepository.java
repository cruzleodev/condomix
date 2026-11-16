package br.com.condomix.acesso.persistencia;

import br.com.condomix.acesso.convite.Convite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConviteRepository extends JpaRepository<Convite, UUID> {

	/** Busca pelo código (já transformado em impressão digital). Usado em modo sistema, antes de saber o condomínio. */
	@Query("select c from Convite c join fetch c.pessoa where c.codigoHash = :codigoHash")
	Optional<Convite> porCodigo(@Param("codigoHash") String codigoHash);

	@Query("select c from Convite c where c.pessoa.id = :pessoaId and c.aceitoEm is null and c.canceladoEm is null")
	List<Convite> pendentesDaPessoa(@Param("pessoaId") UUID pessoaId);

}
