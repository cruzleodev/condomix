package br.com.condomix.administracao;

import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.financeiro.AmbienteDoGateway;
import br.com.condomix.financeiro.GatewayDoCondominio;
import br.com.condomix.financeiro.TipoDeGateway;
import br.com.condomix.plataforma.ConsultaDeCondominios;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/** Caso de uso: o administrador liga o boleto e o Pix de um cliente com a chave da conta Asaas do condomínio. */
@Service
class GatewayDoCliente {

	private final GatewayDoCondominio gateway;

	private final ConsultaDeCondominios condominios;

	GatewayDoCliente(GatewayDoCondominio gateway, ConsultaDeCondominios condominios) {
		this.gateway = gateway;
		this.condominios = condominios;
	}

	/** @param disponiveis os gateways que esta instalação oferece (o simulado só em desenvolvimento) */
	record GatewayDoClienteAtual(GatewayDoCondominio.SituacaoDoGateway situacao, Set<TipoDeGateway> disponiveis) {
	}

	GatewayDoClienteAtual situacao(UUID condominioId) {
		conferir(condominioId);
		return new GatewayDoClienteAtual(gateway.situacao(condominioId), gateway.disponiveis());
	}

	GatewayDoCondominio.AvisoParaConfigurar configurar(UUID condominioId, TipoDeGateway tipo, AmbienteDoGateway ambiente,
			String chave, UUID administradorId) {
		conferir(condominioId);
		return gateway.configurar(condominioId, tipo, ambiente, chave, administradorId);
	}

	void desativar(UUID condominioId) {
		conferir(condominioId);
		gateway.desativar(condominioId);
	}

	private void conferir(UUID condominioId) {
		if (!condominios.existe(condominioId)) {
			throw new NaoEncontradoException("Condomínio não encontrado");
		}
	}

}
