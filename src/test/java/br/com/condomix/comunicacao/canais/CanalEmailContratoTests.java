package br.com.condomix.comunicacao.canais;

import br.com.condomix.compartilhado.EnvioDeEmail;
import br.com.condomix.comunicacao.aplicacao.PropriedadesDasNotificacoes;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CanalEmailContratoTests extends CanalDeNotificacaoContrato {

	private final List<EnvioDeEmail.MensagemDeEmail> enviados = new ArrayList<>();

	private boolean foraDoAr;

	private final CanalEmail canal = new CanalEmail(new EnvioDeEmail() {
		@Override
		public boolean disponivel() {
			return true;
		}

		@Override
		public void enviar(MensagemDeEmail mensagem) {
			if (foraDoAr) {
				throw new IllegalStateException("O servidor de e-mail não respondeu");
			}
			enviados.add(mensagem);
		}
	}, new PropriedadesDasNotificacoes("https://app.condomix.com.br", null, null, false));

	@Override
	protected CanalDeNotificacao canal() {
		return canal;
	}

	@Override
	protected void provedorForaDoAr() {
		foraDoAr = true;
	}

	@Override
	protected String entregue() {
		var m = enviados.getLast();
		assertThat(m.para()).isEqualTo("ana@teste.com");
		assertThat(m.linkDoBotao()).isEqualTo("https://app.condomix.com.br/reservas");
		return m.assunto();
	}

}
