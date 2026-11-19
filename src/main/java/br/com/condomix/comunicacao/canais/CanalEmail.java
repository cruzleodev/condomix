package br.com.condomix.comunicacao.canais;

import br.com.condomix.acesso.DestinatariosDeNotificacoes.Destinatario;
import br.com.condomix.compartilhado.EnvioDeEmail;
import br.com.condomix.compartilhado.EnvioDeEmail.MensagemDeEmail;
import br.com.condomix.comunicacao.aplicacao.PropriedadesDasNotificacoes;
import br.com.condomix.comunicacao.dominio.Canal;
import org.springframework.stereotype.Component;

@Component
class CanalEmail implements CanalDeNotificacao {

	private final EnvioDeEmail correio;

	private final PropriedadesDasNotificacoes propriedades;

	CanalEmail(EnvioDeEmail correio, PropriedadesDasNotificacoes propriedades) {
		this.correio = correio;
		this.propriedades = propriedades;
	}

	@Override
	public Canal canal() {
		return Canal.EMAIL;
	}

	@Override
	public boolean disponivel() {
		return correio.disponivel();
	}

	@Override
	public boolean entregar(ConteudoDaNotificacao conteudo, Destinatario destinatario) {
		try {
			correio.enviar(new MensagemDeEmail(destinatario.email(), destinatario.nome(), conteudo.titulo(), conteudo.texto(),
					conteudo.link() == null ? propriedades.enderecoDoApp() : propriedades.enderecoDoApp() + conteudo.link(),
					"Abrir no Condomix",
					"Você recebe este e-mail por causa dos avisos de " + conteudo.grupo().rotulo().toLowerCase(java.util.Locale.ROOT)
							+ ". Para não receber mais, no app vá em Conta → Notificações."));
			return true;
		}
		catch (IllegalStateException e) {
			throw new FalhaNaEntrega(e.getMessage(), e);
		}
	}

}
