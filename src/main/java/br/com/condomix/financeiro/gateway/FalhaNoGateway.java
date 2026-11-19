package br.com.condomix.financeiro.gateway;

/** O gateway recusou o pedido ou não respondeu. A mensagem vai para o síndico: em português, sem detalhes técnicos. */
public class FalhaNoGateway extends RuntimeException {

	public FalhaNoGateway(String mensagem) {
		super(mensagem);
	}

	public FalhaNoGateway(String mensagem, Throwable causa) {
		super(mensagem, causa);
	}

}
