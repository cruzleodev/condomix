package br.com.condomix.comunicacao.email;

import br.com.condomix.compartilhado.EnvioDeEmail;
import br.com.condomix.comunicacao.aplicacao.PropriedadesDasNotificacoes;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;

/**
 * E-mail por SMTP: funciona com qualquer provedor (Amazon SES, Brevo, Resend...), configurado em {@code spring.mail.*}.
 * Em desenvolvimento, o Mailpit (compose) recebe tudo e mostra em http://localhost:8025. Sem {@code spring.mail.host},
 * o e-mail fica desligado.
 */
@Component
@EnableConfigurationProperties(PropriedadesDasNotificacoes.class)
class EnvioDeEmailSmtp implements EnvioDeEmail {

	private final ObjectProvider<JavaMailSender> correio;

	private final PropriedadesDasNotificacoes propriedades;

	EnvioDeEmailSmtp(ObjectProvider<JavaMailSender> correio, PropriedadesDasNotificacoes propriedades) {
		this.correio = correio;
		this.propriedades = propriedades;
	}

	@Override
	public boolean disponivel() {
		return correio.getIfAvailable() != null;
	}

	@Override
	public void enviar(MensagemDeEmail m) {
		JavaMailSender servidor = correio.getIfAvailable();
		if (servidor == null) {
			throw new IllegalStateException("O envio de e-mail não está configurado (spring.mail.host)");
		}
		try {
			var mensagem = servidor.createMimeMessage();
			var ajudante = new MimeMessageHelper(mensagem, true, StandardCharsets.UTF_8.name());
			ajudante.setFrom(propriedades.remetente());
			ajudante.setTo(m.para());
			ajudante.setSubject(m.assunto());
			ajudante.setText(textoSimples(m), html(m));
			servidor.send(mensagem);
		}
		catch (MessagingException | MailException e) {
			throw new IllegalStateException("O servidor de e-mail recusou ou não respondeu: " + e.getMessage(), e);
		}
	}

	private static String textoSimples(MensagemDeEmail m) {
		return "Olá, " + m.nomeDoDestinatario() + ".\n\n" + m.texto()
				+ (m.linkDoBotao() == null ? "" : "\n\n" + m.rotuloDoBotao() + ": " + m.linkDoBotao())
				+ (m.rodape() == null ? "" : "\n\n" + m.rodape());
	}

	/** Um HTML simples, com estilos na linha (os clientes de e-mail ignoram folhas de estilo). */
	private static String html(MensagemDeEmail m) {
		String botao = m.linkDoBotao() == null ? "" : """
				<p style="margin:24px 0"><a href="%s" style="background:#0f766e;color:#ffffff;padding:12px 20px;\
				border-radius:10px;text-decoration:none;font-weight:600;display:inline-block">%s</a></p>"""
				.formatted(HtmlUtils.htmlEscape(m.linkDoBotao()), HtmlUtils.htmlEscape(m.rotuloDoBotao()));
		String rodape = m.rodape() == null ? "" : "<p style=\"color:#64748b;font-size:12px;margin-top:32px\">%s</p>"
				.formatted(HtmlUtils.htmlEscape(m.rodape()));
		return """
				<div style="font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:0 auto;color:#0f172a">
				<p style="font-size:18px;font-weight:700;color:#0f766e">Condomix</p>
				<p>Olá, %s.</p>
				<p style="white-space:pre-line">%s</p>
				%s%s
				</div>""".formatted(HtmlUtils.htmlEscape(m.nomeDoDestinatario()), HtmlUtils.htmlEscape(m.texto()), botao, rodape);
	}

}
