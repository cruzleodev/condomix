package br.com.condomix.comunicacao.email;

import br.com.condomix.compartilhado.EnvioDeEmail.MensagemDeEmail;
import br.com.condomix.comunicacao.aplicacao.PropriedadesDasNotificacoes;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Monta o e-mail de verdade (texto simples e HTML), sem enviar: o servidor SMTP é trocado por uma lista. */
class EnvioDeEmailSmtpTests {

	@Test
	void montaOEmailComTextoSimplesEHtmlEscapado() throws Exception {
		List<MimeMessage> enviados = new ArrayList<>();
		JavaMailSenderImpl servidor = new JavaMailSenderImpl() {
			@Override
			public void send(MimeMessage mensagem) {
				enviados.add(mensagem);
			}
		};
		@SuppressWarnings("unchecked")
		ObjectProvider<JavaMailSender> provedor = mock(ObjectProvider.class);
		when(provedor.getIfAvailable()).thenReturn(servidor);
		var envio = new EnvioDeEmailSmtp(provedor, new PropriedadesDasNotificacoes(null, "Condomix <avisos@condomix.com.br>", null, false));

		envio.enviar(new MensagemDeEmail("ana@teste.com", "Ana <Proprietária>", "Nova cobrança", "Consumo de gás: R$ 87,40.",
				"http://localhost:5173/debitos", "Abrir no Condomix", "Para não receber mais, vá em Conta."));

		MimeMessage m = enviados.getFirst();
		assertThat(m.getSubject()).isEqualTo("Nova cobrança");
		assertThat(m.getAllRecipients()[0].toString()).isEqualTo("ana@teste.com");
		assertThat(m.getFrom()[0].toString()).contains("avisos@condomix.com.br");
		assertThat(m.getContent()).isInstanceOf(Multipart.class);
		var bruto = new ByteArrayOutputStream();
		m.writeTo(bruto);
		String corpo = bruto.toString(java.nio.charset.StandardCharsets.UTF_8);
		assertThat(corpo).contains("text/plain").contains("text/html").contains("http://localhost:5173/debitos");
		assertThat(corpo).contains("Ana &lt;Propriet"); // no HTML, o nome vai escapado
	}

}
