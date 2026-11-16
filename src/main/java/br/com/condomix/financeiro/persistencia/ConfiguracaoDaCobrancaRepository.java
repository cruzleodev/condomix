package br.com.condomix.financeiro.persistencia;

import br.com.condomix.financeiro.dominio.ConfiguracaoDaCobranca;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConfiguracaoDaCobrancaRepository extends JpaRepository<ConfiguracaoDaCobranca, UUID> {

	/** A do condomínio atual (o filtro de condomínio deixa no máximo uma). */
	Optional<ConfiguracaoDaCobranca> findFirstBy();

}
