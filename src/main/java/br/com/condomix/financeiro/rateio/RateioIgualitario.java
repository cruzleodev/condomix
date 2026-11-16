package br.com.condomix.financeiro.rateio;

import br.com.condomix.acesso.CadastroParaCobranca.UnidadeCobravel;
import br.com.condomix.financeiro.dominio.TipoDeRateio;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
class RateioIgualitario implements CalculoDoRateio {

	@Override
	public TipoDeRateio tipo() {
		return TipoDeRateio.IGUALITARIO;
	}

	@Override
	public Rateio calcular(BigDecimal valorTotal, List<UnidadeCobravel> unidades) {
		ValorTotal.exigir(valorTotal);
		Map<UUID, BigDecimal> pesos = new LinkedHashMap<>();
		unidades.forEach(u -> pesos.put(u.id(), BigDecimal.ONE));
		return new Rateio(DistribuicaoDeCentavos.distribuir(valorTotal, pesos), List.of());
	}

}
