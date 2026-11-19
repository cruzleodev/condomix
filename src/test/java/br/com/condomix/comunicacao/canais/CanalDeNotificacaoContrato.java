package br.com.condomix.comunicacao.canais;

import br.com.condomix.acesso.DestinatariosDeNotificacoes.Destinatario;
import br.com.condomix.comunicacao.dominio.GrupoDeNotificacao;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O contrato de todo {@link CanalDeNotificacao} (SOLID: L): com destino, entrega e devolve verdadeiro; com o provedor
 * fora do ar, lança {@link FalhaNaEntrega} (para tentar de novo), nunca outra exceção.
 */
abstract class CanalDeNotificacaoContrato {

	protected static final Destinatario ANA = new Destinatario(UUID.randomUUID(), "ana@teste.com", "Ana");

	protected static final ConteudoDaNotificacao CONTEUDO = new ConteudoDaNotificacao(UUID.randomUUID(), GrupoDeNotificacao.RESERVAS,
			"Reserva confirmada", "Salão de festas, sábado, 20/12/2026, Noite (18h às 23h).", "/reservas");

	protected abstract CanalDeNotificacao canal();

	/** Daqui em diante, o provedor (servidor de e-mail, serviço de push) não responde ou recusa. */
	protected abstract void provedorForaDoAr();

	/** O que chegou ao destino, para conferir o título. */
	protected abstract String entregue();

	@Test
	void entregaComDestinoEDevolveVerdadeiro() {
		assertThat(canal().disponivel()).isTrue();
		assertThat(canal().canal()).isNotNull();
		assertThat(canal().entregar(CONTEUDO, ANA)).isTrue();
		assertThat(entregue()).contains("Reserva confirmada");
	}

	@Test
	void provedorForaDoArViraFalhaParaTentarDeNovo() {
		provedorForaDoAr();
		assertThatThrownBy(() -> canal().entregar(CONTEUDO, ANA)).isInstanceOf(FalhaNaEntrega.class);
	}

}
