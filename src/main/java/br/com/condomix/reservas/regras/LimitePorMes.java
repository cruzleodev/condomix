package br.com.condomix.reservas.regras;

import br.com.condomix.compartilhado.FormatoBrasileiro;
import br.com.condomix.reservas.dominio.SituacaoDaReserva;
import br.com.condomix.reservas.persistencia.ReservaRepository;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.Optional;

/** Cada unidade reserva o espaço no máximo tantas vezes por mês (o mês da data reservada). */
@Component
class LimitePorMes implements RegraDeReserva {

	private final ReservaRepository reservas;

	LimitePorMes(ReservaRepository reservas) {
		this.reservas = reservas;
	}

	@Override
	public Optional<String> recusa(PedidoDeReserva pedido) {
		Integer limite = pedido.espaco().getRegras().limitePorMes();
		if (limite == null) {
			return Optional.empty();
		}
		YearMonth mes = YearMonth.from(pedido.data());
		long feitas = reservas.countByEspacoIdAndUnidadeIdAndDataBetweenAndSituacao(pedido.espaco().getId(),
				pedido.unidadeId(), mes.atDay(1), mes.atEndOfMonth(), SituacaoDaReserva.CONFIRMADA);
		if (feitas < limite) {
			return Optional.empty();
		}
		return Optional.of("A unidade já tem " + feitas + (feitas == 1 ? " reserva" : " reservas") + " deste espaço em "
				+ FormatoBrasileiro.mes(mes) + ", o limite do condomínio.");
	}

}
