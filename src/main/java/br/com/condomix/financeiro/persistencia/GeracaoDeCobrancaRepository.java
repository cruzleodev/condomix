package br.com.condomix.financeiro.persistencia;

import br.com.condomix.financeiro.dominio.GeracaoDeCobranca;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface GeracaoDeCobrancaRepository extends JpaRepository<GeracaoDeCobranca, UUID> {

	@Query("select count(g) > 0 from GeracaoDeCobranca g where g.competencia = :competencia and g.canceladaEm is null")
	boolean existeValida(@Param("competencia") LocalDate competencia);

	@Query("select g from GeracaoDeCobranca g order by g.competencia desc, g.geradaEm desc")
	List<GeracaoDeCobranca> historico();

}
