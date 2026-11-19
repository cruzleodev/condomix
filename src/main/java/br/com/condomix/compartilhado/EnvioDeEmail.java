package br.com.condomix.compartilhado;

/**
 * Envia um e-mail agora (SMTP do provedor configurado). Fica aqui, e não na comunicação, porque o acesso também
 * usa (convite) e a comunicação depende do acesso: assim não há ciclo. A implementação fica em
 * {@code comunicacao.email}.
 */
public interface EnvioDeEmail {

	/** Falso quando o SMTP não foi configurado (ex.: nos testes): nada é enviado. */
	boolean disponivel();

	/** Lança {@link IllegalStateException} se o provedor recusar ou não responder. */
	void enviar(MensagemDeEmail mensagem);

	/**
	 * @param linkDoBotao   endereço completo (ex.: https://app.condomix.com.br/debitos); vazio, sem botão
	 * @param rodape        texto pequeno no fim (ex.: como deixar de receber)
	 */
	record MensagemDeEmail(String para, String nomeDoDestinatario, String assunto, String texto, String linkDoBotao,
			String rotuloDoBotao, String rodape) {
	}

}
