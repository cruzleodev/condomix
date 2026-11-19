package br.com.condomix.financeiro;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * O gateway de pagamentos de um condomínio, configurado pelo administrador da plataforma (módulo administração).
 * A chave de API fica cifrada e nunca é devolvida.
 */
public interface GatewayDoCondominio {

	SituacaoDoGateway situacao(UUID condominioId);

	/**
	 * Confere a chave no gateway e liga o boleto e o Pix. Configurar de novo troca a chave e o token do aviso.
	 *
	 * @return o endereço e o token para cadastrar o aviso (webhook) na conta do condomínio no gateway
	 */
	AvisoParaConfigurar configurar(UUID condominioId, TipoDeGateway gateway, AmbienteDoGateway ambiente, String chave,
			UUID administradorId);

	/** Para de emitir boletos novos. Os já emitidos continuam valendo e sendo baixados. */
	void desativar(UUID condominioId);

	Set<TipoDeGateway> disponiveis();

	/** {@code configurado} falso: os outros campos vêm vazios. */
	record SituacaoDoGateway(boolean configurado, boolean ativo, TipoDeGateway gateway, AmbienteDoGateway ambiente,
			Instant configuradoEm) {
	}

	/** O token só é mostrado agora: guarde na configuração de avisos do gateway. */
	record AvisoParaConfigurar(String url, String token) {
	}

}
