package br.com.condomix.plataforma;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Consulta de condomínios oferecida aos outros módulos (só leitura). */
public interface ConsultaDeCondominios {

	/** Os condomínios ativos entre os ids informados. Ids inexistentes ou inativos são ignorados. */
	List<ResumoCondominio> ativosEntre(Collection<UUID> ids);

	/**
	 * Os suspensos por falta de pagamento entre os ids informados: a gestão deles ainda entra, só para pagar
	 * a fatura da assinatura.
	 */
	List<ResumoCondominio> suspensosPorFaltaDePagamentoEntre(Collection<UUID> ids);

	/** Se o condomínio existe (ativo ou suspenso). */
	boolean existe(UUID id);

}
