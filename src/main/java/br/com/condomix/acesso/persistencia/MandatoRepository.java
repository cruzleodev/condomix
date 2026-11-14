package br.com.condomix.acesso.persistencia;

import br.com.condomix.acesso.Cargo;
import br.com.condomix.acesso.mandato.Mandato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MandatoRepository extends JpaRepository<Mandato, UUID> {

	/** Histórico completo do condomínio atual, mais recentes primeiro. */
	@Query("select m from Mandato m join fetch m.pessoa order by m.inicio desc, m.cargo")
	List<Mandato> historico();

	@Query("select m from Mandato m join fetch m.pessoa where m.id = :id")
	Optional<Mandato> comOcupante(@Param("id") UUID id);

	/** Mandatos ainda sem data de encerramento (vigentes, a vencer, prorrogados ou futuros). */
	@Query("select m from Mandato m join fetch m.pessoa where m.cargo = :cargo and m.encerradoEm is null")
	List<Mandato> emAberto(@Param("cargo") Cargo cargo);

	/**
	 * Mandatos que dão acesso ao usuário na data, em todos os condomínios. Inclui os prorrogados
	 * (prazo vencido, sem encerramento). Usado pelo login, em modo sistema.
	 */
	@Query("""
			select m from Mandato m join m.pessoa p
			where p.usuarioId = :usuarioId
			  and m.inicio <= :data
			  and (m.encerradoEm is null or m.encerradoEm >= :data)
			""")
	List<Mandato> comAcessoNaData(@Param("usuarioId") UUID usuarioId, @Param("data") LocalDate data);

}
