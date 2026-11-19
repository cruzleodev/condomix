package br.com.condomix.financeiro.simulado;

import br.com.condomix.financeiro.TipoDeGateway;
import br.com.condomix.financeiro.gateway.FalhaNoGateway;
import br.com.condomix.financeiro.gateway.GatewayDePagamento;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gateway de mentira, para desenvolver e testar sem conta no Asaas. Segue as mesmas regras do Asaas que
 * importam para o Condomix (exige CPF/CNPJ do pagador, não aceita vencimento no passado) e passa no mesmo
 * teste de contrato. Os boletos ficam só na memória: reiniciar a API esquece quais foram pagos.
 *
 * <p>Só existe com {@code condomix.gateway.simulado=true} (desenvolvimento e testes).
 */
@Component
@ConditionalOnProperty(name = "condomix.gateway.simulado", havingValue = "true")
public class GatewaySimulado implements GatewayDePagamento {

	/** Tarifa fixa por pagamento, parecida com a do Asaas. */
	public static final BigDecimal TARIFA = new BigDecimal("1.99");

	private final Map<String, Emitido> emitidos = new ConcurrentHashMap<>();

	private final Clock relogio;

	public GatewaySimulado(Clock relogio) {
		this.relogio = relogio;
	}

	private record Emitido(BigDecimal valor, LocalDate vencimento, boolean cancelado, PagamentoConfirmado pagamento) {
	}

	@Override
	public TipoDeGateway tipo() {
		return TipoDeGateway.SIMULADO;
	}

	@Override
	public void validar(Credencial credencial) {
		if (credencial.chave() == null || credencial.chave().isBlank() || credencial.chave().startsWith("invalida")) {
			throw new FalhaNoGateway("A chave de API não foi aceita pelo gateway");
		}
	}

	@Override
	public BoletoEmitido emitir(Credencial credencial, PedidoDeBoleto pedido) {
		validar(credencial);
		if (pedido.pagador().documento() == null || pedido.pagador().documento().isBlank()) {
			throw new FalhaNoGateway("O CPF ou CNPJ do pagador é obrigatório");
		}
		if (pedido.vencimento().isBefore(LocalDate.now(relogio))) {
			throw new FalhaNoGateway("O vencimento não pode ser no passado");
		}
		String id = "sim_" + UUID.randomUUID().toString().replace("-", "");
		emitidos.put(id, new Emitido(pedido.valor(), pedido.vencimento(), false, null));
		String centavos = pedido.valor().movePointRight(2).toBigInteger().toString();
		return new BoletoEmitido(id, null,
				"00190.00009 01234.567004 00000.000000 1 " + "0".repeat(Math.max(0, 14 - centavos.length())) + centavos,
				"00020126580014BR.GOV.BCB.PIX0136" + id + "5204000053039865802BR5925CONDOMIX SIMULADO6009SAO PAULO6304ABCD",
				null);
	}

	@Override
	public void cancelar(Credencial credencial, String idNoGateway) {
		emitidos.computeIfPresent(idNoGateway, (id, e) -> {
			if (e.pagamento() != null) {
				throw new FalhaNoGateway("Este boleto já foi pago e não pode ser cancelado");
			}
			return new Emitido(e.valor(), e.vencimento(), true, null);
		});
	}

	@Override
	public Optional<PagamentoConfirmado> pagamento(Credencial credencial, String idNoGateway) {
		return Optional.ofNullable(emitidos.get(idNoGateway)).map(Emitido::pagamento);
	}

	/** Faz de conta que o morador pagou. Sem {@code valor}, paga o valor do boleto. */
	public void simularPagamento(String idNoGateway, LocalDate data, BigDecimal valor, FormaDoPagamento forma) {
		emitidos.compute(idNoGateway, (id, e) -> {
			if (e == null) {
				throw new FalhaNoGateway("Boleto não encontrado no gateway simulado (a API foi reiniciada?)");
			}
			if (e.cancelado()) {
				throw new FalhaNoGateway("Este boleto foi cancelado");
			}
			BigDecimal pago = valor != null ? valor : e.valor();
			return new Emitido(e.valor(), e.vencimento(), false, new PagamentoConfirmado(data, pago, TARIFA, forma));
		});
	}

}
