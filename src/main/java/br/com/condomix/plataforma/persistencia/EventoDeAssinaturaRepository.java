package br.com.condomix.plataforma.persistencia;

import br.com.condomix.plataforma.historico.EventoDeAssinatura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EventoDeAssinaturaRepository extends JpaRepository<EventoDeAssinatura, UUID> {

	@Query("select e from EventoDeAssinatura e left join fetch e.plano where e.condominioId = :condominioId order by e.momento desc")
	List<EventoDeAssinatura> doCondominio(@Param("condominioId") UUID condominioId);

}
