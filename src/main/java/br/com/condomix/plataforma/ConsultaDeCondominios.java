package br.com.condomix.plataforma;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Consulta de condomínios oferecida aos outros módulos (só leitura). */
public interface ConsultaDeCondominios {

	/** Os condomínios ativos entre os ids informados. Ids inexistentes ou inativos são ignorados. */
	List<ResumoCondominio> ativosEntre(Collection<UUID> ids);

}
