package br.com.condomix.financeiro.persistencia;

import br.com.condomix.financeiro.dominio.ContaFinanceira;
import br.com.condomix.financeiro.dominio.TipoDeConta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContaFinanceiraRepository extends JpaRepository<ContaFinanceira, UUID> {

	Optional<ContaFinanceira> findFirstByRecebimentoPadraoTrue();

	Optional<ContaFinanceira> findFirstByTipoAndAtivaTrue(TipoDeConta tipo);

	List<ContaFinanceira> findAllByOrderByNome();

}
