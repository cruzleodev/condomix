package br.com.condomix.comunicacao.ouvintes;

import br.com.condomix.comunicacao.aplicacao.CentralDeNotificacoes;
import br.com.condomix.comunicacao.aplicacao.CentralDeNotificacoes.NovaNotificacao;
import br.com.condomix.comunicacao.dominio.GrupoDeNotificacao;
import br.com.condomix.reservas.ReservaAmanha;
import br.com.condomix.reservas.ReservaDesfeita;
import br.com.condomix.reservas.ReservaFeita;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import static br.com.condomix.compartilhado.FormatoBrasileiro.diaDaSemana;
import static br.com.condomix.compartilhado.FormatoBrasileiro.hora;
import static br.com.condomix.compartilhado.FormatoBrasileiro.moeda;

/** Reservas: avisa quem usa a unidade (proprietário, inquilino e morador). */
@Component
class NotificacoesDasReservas {

	private final CentralDeNotificacoes central;

	NotificacoesDasReservas(CentralDeNotificacoes central) {
		this.central = central;
	}

	@Async
	@TransactionalEventListener
	void reservaFeita(ReservaFeita e) {
		String texto = e.espaco() + ", " + diaDaSemana(e.data()) + ", " + e.horario() + " (" + hora(e.inicio()) + " às " + hora(e.fim()) + ")."
				+ (e.pelaGestao() ? " Reserva feita pela administração do condomínio." : "")
				+ (e.valor() != null && e.valor().signum() > 0 ? " O aluguel de " + moeda(e.valor()) + " está em Meus débitos." : "");
		central.notificar(e.condominioId(), d -> d.ocupantesDaUnidade(e.unidadeId()), new NovaNotificacao(GrupoDeNotificacao.RESERVAS,
				"reserva-feita:" + e.reservaId(), "Reserva confirmada", texto, "/reservas"));
	}

	@Async
	@TransactionalEventListener
	void reservaDesfeita(ReservaDesfeita e) {
		String texto = e.espaco() + ", " + diaDaSemana(e.data()) + ", " + e.horario() + "."
				+ (e.pelaGestao() ? " Cancelada pela administração do condomínio." : "")
				+ (e.motivo() == null ? "" : " Motivo: " + e.motivo() + ".")
				+ (e.aluguelMantido() ? " Como foi fora do prazo, o aluguel continua em Meus débitos." : "");
		central.notificar(e.condominioId(), d -> d.ocupantesDaUnidade(e.unidadeId()), new NovaNotificacao(GrupoDeNotificacao.RESERVAS,
				"reserva-cancelada:" + e.reservaId(), "Reserva cancelada", texto, "/reservas"));
	}

	@Async
	@TransactionalEventListener
	void reservaAmanha(ReservaAmanha e) {
		central.notificar(e.condominioId(), d -> d.ocupantesDaUnidade(e.unidadeId()), new NovaNotificacao(GrupoDeNotificacao.RESERVAS,
				"reserva-amanha:" + e.reservaId(), "Sua reserva é amanhã",
				e.espaco() + ", " + diaDaSemana(e.data()) + ", " + e.horario() + " (" + hora(e.inicio()) + " às " + hora(e.fim()) + ").",
				"/reservas"));
	}

}
