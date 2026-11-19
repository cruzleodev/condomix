package br.com.condomix.comunicacao.canais;

/** A entrega falhou, mas pode dar certo numa nova tentativa (servidor fora do ar, recusa temporária). */
public class FalhaNaEntrega extends RuntimeException {

	public FalhaNaEntrega(String mensagem) {
		super(mensagem);
	}

	public FalhaNaEntrega(String mensagem, Throwable causa) {
		super(mensagem, causa);
	}

}
