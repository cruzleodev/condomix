package br.com.condomix.financeiro.rateio;

import br.com.condomix.acesso.CadastroParaCobranca.UnidadeCobravel;
import br.com.condomix.financeiro.dominio.TipoDeRateio;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Proporcional à fração ideal. Se as frações não somam exatamente 1, divide proporcionalmente à soma
 * (o total cobrado continua igual ao informado) e avisa o síndico.
 */
@Component
class RateioPorFracaoIdeal implements CalculoDoRateio {

	@Override
	public TipoDeRateio tipo() {
		return TipoDeRateio.FRACAO_IDEAL;
	}

	@Override
	public Rateio calcular(BigDecimal valorTotal, List<UnidadeCobravel> unidades) {
		ValorTotal.exigir(valorTotal);
		List<String> semFracao = unidades.stream().filter(u -> u.fracaoIdeal() == null).map(UnidadeCobravel::descricao).toList();
		if (!semFracao.isEmpty()) {
			throw new IllegalArgumentException("Defina a fração ideal destas unidades antes de gerar: " + String.join(", ", semFracao));
		}
		Map<UUID, BigDecimal> pesos = new LinkedHashMap<>();
		unidades.forEach(u -> pesos.put(u.id(), u.fracaoIdeal()));
		BigDecimal soma = pesos.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		List<String> avisos = new ArrayList<>();
		if (soma.compareTo(BigDecimal.ONE) != 0) {
			avisos.add("As frações ideais das unidades ativas somam " + soma.stripTrailingZeros().toPlainString()
					+ " (o certo é 1). O valor foi dividido proporcionalmente à soma.");
		}
		return new Rateio(DistribuicaoDeCentavos.distribuir(valorTotal, pesos), avisos);
	}

}
