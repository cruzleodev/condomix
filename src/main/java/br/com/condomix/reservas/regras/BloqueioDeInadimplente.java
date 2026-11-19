package br.com.condomix.reservas.regras;

import br.com.condomix.financeiro.CobrancasParaReservas;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Unidade com cobrança vencida em aberto não reserva (quando o espaço liga esta regra). */
@Component
class BloqueioDeInadimplente implements RegraDeReserva {

	private final CobrancasParaReservas cobrancas;

	BloqueioDeInadimplente(CobrancasParaReservas cobrancas) {
		this.cobrancas = cobrancas;
	}

	@Override
	public Optional<String> recusa(PedidoDeReserva pedido) {
		if (!pedido.espaco().getRegras().bloqueiaInadimplente()
				|| !cobrancas.unidadeInadimplente(pedido.unidadeId(), pedido.hoje())) {
			return Optional.empty();
		}
		return Optional.of("A unidade tem cobrança vencida em aberto. Regularize em Meus débitos para poder reservar.");
	}

}
