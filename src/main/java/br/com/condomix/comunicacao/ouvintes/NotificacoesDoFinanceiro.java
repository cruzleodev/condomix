package br.com.condomix.comunicacao.ouvintes;

import br.com.condomix.comunicacao.aplicacao.CentralDeNotificacoes;
import br.com.condomix.comunicacao.aplicacao.CentralDeNotificacoes.NovaNotificacao;
import br.com.condomix.comunicacao.dominio.GrupoDeNotificacao;
import br.com.condomix.financeiro.CobrancaDisponivel;
import br.com.condomix.financeiro.CobrancaPaga;
import br.com.condomix.financeiro.CobrancaVenceEmBreve;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import static br.com.condomix.compartilhado.FormatoBrasileiro.data;
import static br.com.condomix.compartilhado.FormatoBrasileiro.moeda;

/**
 * Cobranças do morador: avisa quem paga a unidade (proprietário e inquilino). Os eventos ficam guardados (Spring
 * Modulith) até a notificação ser criada: se falhar, são reprocessados.
 */
@Component
class NotificacoesDoFinanceiro {

	private final CentralDeNotificacoes central;

	NotificacoesDoFinanceiro(CentralDeNotificacoes central) {
		this.central = central;
	}

	@Async
	@TransactionalEventListener
	void cobrancaDisponivel(CobrancaDisponivel e) {
		central.notificar(e.condominioId(), d -> d.pagadoresDaUnidade(e.unidadeId()), new NovaNotificacao(GrupoDeNotificacao.COBRANCAS,
				"cobranca-disponivel:" + e.cobrancaId(),
				e.comBoletoEPix() ? "Boleto e Pix disponíveis" : "Nova cobrança",
				e.descricao() + ": " + moeda(e.valor()) + ", vence em " + data(e.vencimento()) + "."
						+ (e.comBoletoEPix() ? " Pague pelo app, em Meus débitos, com Pix ou boleto." : ""),
				"/debitos"));
	}

	@Async
	@TransactionalEventListener
	void cobrancaVenceEmBreve(CobrancaVenceEmBreve e) {
		central.notificar(e.condominioId(), d -> d.pagadoresDaUnidade(e.unidadeId()), new NovaNotificacao(GrupoDeNotificacao.COBRANCAS,
				"cobranca-vence:" + e.cobrancaId() + ":" + e.vencimento(),
				"Vencimento chegando",
				e.descricao() + ": " + moeda(e.valor()) + ", vence em " + data(e.vencimento()) + ".",
				"/debitos"));
	}

	@Async
	@TransactionalEventListener
	void cobrancaPaga(CobrancaPaga e) {
		central.notificar(e.condominioId(), d -> d.pagadoresDaUnidade(e.unidadeId()), new NovaNotificacao(GrupoDeNotificacao.COBRANCAS,
				"cobranca-paga:" + e.cobrancaId() + ":" + e.data(),
				"Pagamento confirmado",
				e.descricao() + ": recebemos " + moeda(e.valorPago()) + " em " + data(e.data()) + ". Obrigado!",
				"/debitos"));
	}

}
