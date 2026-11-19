package br.com.condomix.pagamentos;

import br.com.condomix.compartilhado.RegraDeNegocioException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Os gateways disponíveis nesta instalação (o simulado só em desenvolvimento e testes). */
@Component
public class Gateways {

	private final Map<TipoDeGateway, GatewayDePagamento> porTipo = new EnumMap<>(TipoDeGateway.class);

	public Gateways(List<GatewayDePagamento> implementacoes) {
		implementacoes.forEach(g -> porTipo.put(g.tipo(), g));
	}

	public GatewayDePagamento de(TipoDeGateway tipo) {
		GatewayDePagamento gateway = porTipo.get(tipo);
		if (gateway == null) {
			throw RegraDeNegocioException.invalido("O gateway " + tipo + " não está disponível nesta instalação");
		}
		return gateway;
	}

	public Set<TipoDeGateway> disponiveis() {
		return porTipo.keySet();
	}

}
