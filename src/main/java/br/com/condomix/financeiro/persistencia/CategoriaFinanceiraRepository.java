package br.com.condomix.financeiro.persistencia;

import br.com.condomix.financeiro.dominio.CategoriaFinanceira;
import br.com.condomix.financeiro.dominio.CategoriaPadrao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoriaFinanceiraRepository extends JpaRepository<CategoriaFinanceira, UUID> {

	Optional<CategoriaFinanceira> findByCodigo(CategoriaPadrao codigo);

	List<CategoriaFinanceira> findAllByOrderByNaturezaAscNomeAsc();

}
