package br.com.condomix.plataforma.persistencia;

import br.com.condomix.plataforma.Assinatura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface AssinaturaRepository extends JpaRepository<Assinatura, UUID> {

	List<Assinatura> findByTenantId(UUID tenantId);

	/** Assinaturas não canceladas de planos base (uma por condomínio), com o plano carregado. */
	@Query("""
			select a from Assinatura a join fetch a.plano p
			where a.status <> br.com.condomix.plataforma.StatusAssinatura.CANCELADA
			  and p.tipo = br.com.condomix.plataforma.TipoPlano.BASE
			""")
	List<Assinatura> vigentesDePlanoBase();

}
