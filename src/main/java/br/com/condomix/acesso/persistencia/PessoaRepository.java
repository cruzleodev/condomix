package br.com.condomix.acesso.persistencia;

import br.com.condomix.acesso.Pessoa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PessoaRepository extends JpaRepository<Pessoa, UUID> {

	List<Pessoa> findByTenantIdOrderByNome(UUID tenantId);

	/** No condomínio atual (o documento é único por condomínio). */
	Optional<Pessoa> findByDocumento(String documento);

	/** Por parte do nome ou pelo documento exato, sem diferenciar maiúsculas. */
	@Query("""
			select p from Pessoa p
			where lower(p.nome) like lower(concat('%', :termo, '%')) or p.documento = upper(:termo)
			order by p.nome
			""")
	List<Pessoa> buscar(@Param("termo") String termo);

	@Query("select p from Pessoa p order by p.nome")
	List<Pessoa> todasEmOrdem();

}
