package br.com.condomix.reservas.persistencia;

import br.com.condomix.reservas.dominio.Espaco;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EspacoRepository extends JpaRepository<Espaco, UUID> {

	List<Espaco> findAllByOrderByNome();

	boolean existsByNomeIgnoreCaseAndIdNot(String nome, UUID id);

	boolean existsByNomeIgnoreCase(String nome);

	/** Trava o espaço até o fim da transação: duas reservas ao mesmo tempo no mesmo espaço passam uma de cada vez. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from Espaco e where e.id = :id")
	Optional<Espaco> paraReservar(UUID id);

}
