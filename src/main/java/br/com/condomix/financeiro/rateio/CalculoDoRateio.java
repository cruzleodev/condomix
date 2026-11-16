package br.com.condomix.financeiro.rateio;

import br.com.condomix.acesso.CadastroParaCobranca.UnidadeCobravel;
import br.com.condomix.financeiro.dominio.TipoDeRateio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Uma regra de divisão da taxa entre as unidades (SOLID: O). Cada regra é um bean; o caso de uso escolhe
 * pela {@link #tipo()}. Uma regra nova entra como uma classe nova, sem mexer nas outras.
 */
public interface CalculoDoRateio {

	TipoDeRateio tipo();

	/**
	 * @param valorTotal o total a dividir; a regra "valor por grupo" não usa (cada grupo tem o seu valor)
	 * @return o valor de cada unidade e eventuais avisos para o síndico
	 * @throws IllegalArgumentException quando faltam dados para a regra (explica o que falta)
	 */
	Rateio calcular(BigDecimal valorTotal, List<UnidadeCobravel> unidades);

	record Rateio(Map<UUID, BigDecimal> valores, List<String> avisos) {
	}

}
