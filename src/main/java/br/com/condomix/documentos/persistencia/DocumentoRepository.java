package br.com.condomix.documentos.persistencia;

import br.com.condomix.documentos.Categoria;
import br.com.condomix.documentos.dominio.Documento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface DocumentoRepository extends JpaRepository<Documento, UUID> {

	Page<Documento> findByCategoria(Categoria categoria, Pageable pagina);

	/**
	 * Busca em português, sem diferenciar acentos, ordenada pela relevância.
	 *
	 * <p><b>Atenção:</b> consultas em SQL puro (nativeQuery) <b>não</b> passam pelo filtro de condomínio
	 * do Hibernate. Por isso o {@code tenant_id} e o {@code removido_em} estão escritos aqui, e o
	 * {@code tenantId} é obrigatório.
	 */
	@Query(nativeQuery = true, value = """
			select d.* from documento d
			where d.tenant_id = :tenantId
			  and d.removido_em is null
			  and (cast(:categoria as varchar) is null or d.categoria = cast(:categoria as varchar))
			  and d.busca @@ websearch_to_tsquery('pt_sem_acento', :termo)
			order by ts_rank(d.busca, websearch_to_tsquery('pt_sem_acento', :termo)) desc, d.enviado_em desc
			""",
			countQuery = """
			select count(*) from documento d
			where d.tenant_id = :tenantId
			  and d.removido_em is null
			  and (cast(:categoria as varchar) is null or d.categoria = cast(:categoria as varchar))
			  and d.busca @@ websearch_to_tsquery('pt_sem_acento', :termo)
			""")
	Page<Documento> buscar(@Param("tenantId") UUID tenantId, @Param("categoria") String categoria,
			@Param("termo") String termo, Pageable pagina);

}
