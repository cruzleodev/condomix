package br.com.condomix.financeiro.aplicacao;

import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.financeiro.AmbienteDoGateway;
import br.com.condomix.financeiro.GatewayDoCondominio;
import br.com.condomix.financeiro.TipoDeGateway;
import br.com.condomix.financeiro.gateway.Gateways;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * Entrada do módulo administração. Trabalha dentro do condomínio pedido: é configuração da plataforma
 * (a conta no gateway), não leitura dos dados internos do condomínio.
 */
@Service
class GatewayDoCondominioService implements GatewayDoCondominio {

	private final ContaDoGateway contas;

	private final Gateways gateways;

	GatewayDoCondominioService(ContaDoGateway contas, Gateways gateways) {
		this.contas = contas;
		this.gateways = gateways;
	}

	@Override
	public SituacaoDoGateway situacao(UUID condominioId) {
		return TenantAtual.executarComo(condominioId, () -> contas.situacao()
				.map(c -> new SituacaoDoGateway(true, c.isAtiva(), c.getGateway(), c.getAmbiente(), c.getConfiguradaEm()))
				.orElse(new SituacaoDoGateway(false, false, null, null, null)));
	}

	@Override
	public AvisoParaConfigurar configurar(UUID condominioId, TipoDeGateway gateway, AmbienteDoGateway ambiente, String chave,
			UUID administradorId) {
		var aviso = TenantAtual.executarComo(condominioId, () -> contas.configurar(gateway, ambiente, chave, administradorId));
		return new AvisoParaConfigurar(aviso.url(), aviso.token());
	}

	@Override
	public void desativar(UUID condominioId) {
		TenantAtual.executarComo(condominioId, () -> {
			contas.desativar();
			return null;
		});
	}

	@Override
	public Set<TipoDeGateway> disponiveis() {
		return gateways.disponiveis();
	}

}
