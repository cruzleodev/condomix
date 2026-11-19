package br.com.condomix.comunicacao.aplicacao;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enderecoDoApp onde o app está (os links dos e-mails e do push), ex.: https://app.condomix.com.br
 * @param remetente     quem aparece como remetente dos e-mails
 * @param contatoPush   contato exigido pelos serviços de push ("mailto:..." ou site)
 * @param automatico    se a entrega e os lembretes rodam sozinhos (desligado nos testes, que chamam na hora)
 */
@ConfigurationProperties("condomix.notificacoes")
public record PropriedadesDasNotificacoes(String enderecoDoApp, String remetente, String contatoPush, Boolean automatico) {

	public PropriedadesDasNotificacoes {
		enderecoDoApp = (enderecoDoApp == null ? "http://localhost:5173" : enderecoDoApp).replaceAll("/+$", "");
		remetente = remetente == null ? "Condomix <avisos@condomix.com.br>" : remetente;
		contatoPush = contatoPush == null ? "mailto:suporte@condomix.com.br" : contatoPush;
		automatico = automatico == null || automatico;
	}

}
