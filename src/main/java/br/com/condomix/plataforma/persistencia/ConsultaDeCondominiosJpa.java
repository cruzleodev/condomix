package br.com.condomix.plataforma.persistencia;

import br.com.condomix.plataforma.Condominio;
import br.com.condomix.plataforma.ConsultaDeCondominios;
import br.com.condomix.plataforma.ResumoCondominio;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
class ConsultaDeCondominiosJpa implements ConsultaDeCondominios {

	private final CondominioRepository condominios;

	ConsultaDeCondominiosJpa(CondominioRepository condominios) {
		this.condominios = condominios;
	}

	@Override
	public List<ResumoCondominio> ativosEntre(Collection<UUID> ids) {
		return condominios.findAllById(ids).stream()
				.filter(Condominio::isAtivo)
				.map(c -> new ResumoCondominio(c.getId(), c.getNome()))
				.toList();
	}

}
