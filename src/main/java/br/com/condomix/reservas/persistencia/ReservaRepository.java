package br.com.condomix.reservas.persistencia;

import br.com.condomix.reservas.dominio.Reserva;
import br.com.condomix.reservas.dominio.SituacaoDaReserva;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReservaRepository extends JpaRepository<Reserva, UUID> {

	List<Reserva> findByEspacoIdAndDataAndSituacao(UUID espacoId, LocalDate data, SituacaoDaReserva situacao);

	List<Reserva> findByEspacoIdAndDataBetweenAndSituacaoOrderByDataAscInicioAsc(UUID espacoId, LocalDate de, LocalDate ate,
			SituacaoDaReserva situacao);

	long countByEspacoIdAndUnidadeIdAndDataBetweenAndSituacao(UUID espacoId, UUID unidadeId, LocalDate de, LocalDate ate,
			SituacaoDaReserva situacao);

	List<Reserva> findByUnidadeIdInAndDataGreaterThanEqualOrderByDataAscInicioAsc(Collection<UUID> unidades, LocalDate desde);

	List<Reserva> findByDataBetweenOrderByDataAscInicioAsc(LocalDate de, LocalDate ate);

	/** Em modo sistema: os condomínios com reserva confirmada na data (lembrete da véspera). */
	@org.springframework.data.jpa.repository.Query("""
			select distinct r.tenantId from Reserva r
			where r.situacao = br.com.condomix.reservas.dominio.SituacaoDaReserva.CONFIRMADA and r.data = :data
			""")
	List<UUID> condominiosComReservaEm(LocalDate data);

}
