package br.com.condomix.acesso.persistencia;

import br.com.condomix.acesso.VinculoUnidade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VinculoUnidadeRepository extends JpaRepository<VinculoUnidade, UUID> {

	List<VinculoUnidade> findByUnidadeId(UUID unidadeId);

	List<VinculoUnidade> findByPessoaId(UUID pessoaId);

}
