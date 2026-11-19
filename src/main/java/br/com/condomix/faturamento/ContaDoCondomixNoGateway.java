package br.com.condomix.faturamento;

import br.com.condomix.pagamentos.AmbienteDoGateway;
import br.com.condomix.pagamentos.TipoDeGateway;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * A conta do Condomix no gateway, que recebe as assinaturas, configurada pelo administrador da plataforma.
 * A chave de API fica cifrada e nunca é devolvida.
 */
public interface ContaDoCondomixNoGateway {

	SituacaoDaContaDeRecebimento situacao();

	/**
	 * Confere a chave no gateway e liga a cobrança das assinaturas. Configurar de novo troca a chave e o token do aviso.
	 *
	 * @return o endereço e o token para cadastrar o aviso (webhook) na conta do Condomix no gateway
	 */
	AvisoDaContaDeRecebimento configurar(TipoDeGateway gateway, AmbienteDoGateway ambiente, String chave, UUID administradorId);

	/** Para de gerar faturas novas. As já emitidas continuam valendo e sendo baixadas. */
	void desativar();

	Set<TipoDeGateway> disponiveis();

	/** {@code configurada} falso: os outros campos vêm vazios. */
	record SituacaoDaContaDeRecebimento(boolean configurada, boolean ativa, TipoDeGateway gateway,
			AmbienteDoGateway ambiente, Instant configuradaEm) {
	}

	/** O token só é mostrado agora: guarde na configuração de avisos do gateway. */
	record AvisoDaContaDeRecebimento(String url, String token) {
	}

}
