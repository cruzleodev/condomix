package br.com.condomix.financeiro.persistencia;

import br.com.condomix.financeiro.dominio.Boleto;
import br.com.condomix.financeiro.dominio.SituacaoDoBoleto;
import br.com.condomix.pagamentos.TipoDeGateway;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BoletoRepository extends JpaRepository<Boleto, UUID> {

	/** O boleto que vale para a cobrança (o que não foi cancelado), no condomínio atual. */
	@Query("select b from Boleto b where b.cobrancaId = :cobrancaId and b.situacao <> br.com.condomix.financeiro.dominio.SituacaoDoBoleto.CANCELADO")
	Optional<Boleto> vigente(@Param("cobrancaId") UUID cobrancaId);

	@Query("select b from Boleto b where b.cobrancaId in :cobrancas and b.situacao <> br.com.condomix.financeiro.dominio.SituacaoDoBoleto.CANCELADO")
	List<Boleto> vigentes(@Param("cobrancas") Collection<UUID> cobrancas);

	Optional<Boleto> findByGatewayAndIdNoGateway(TipoDeGateway gateway, String idNoGateway);

	List<Boleto> findBySituacaoOrderByCriadoEm(SituacaoDoBoleto situacao, Limit limite);

	List<Boleto> findBySituacao(SituacaoDoBoleto situacao);

	/** Condomínios com boletos a emitir ou a cancelar. Usado em modo sistema pelo processador. */
	@Query("""
			select distinct b.tenantId from Boleto b
			where b.situacao in (br.com.condomix.financeiro.dominio.SituacaoDoBoleto.PENDENTE,
			                     br.com.condomix.financeiro.dominio.SituacaoDoBoleto.A_CANCELAR)
			""")
	List<UUID> condominiosComBoletosAProcessar();

}
