package br.com.condomix.faturamento.persistencia;

import br.com.condomix.faturamento.dominio.ContaDeRecebimento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ContaDeRecebimentoRepository extends JpaRepository<ContaDeRecebimento, UUID> {

	/** A única conta, se já foi configurada. */
	default Optional<ContaDeRecebimento> daPlataforma() {
		return findAll().stream().findFirst();
	}

}
