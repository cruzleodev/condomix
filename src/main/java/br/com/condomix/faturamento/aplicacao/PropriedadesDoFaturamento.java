package br.com.condomix.faturamento.aplicacao;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Multa e juros das faturas pagas com atraso (o boleto já sai com eles) e se a rotina e o processador rodam
 * sozinhos (desligados nos testes, que chamam na hora certa).
 */
@ConfigurationProperties("condomix.faturamento")
public record PropriedadesDoFaturamento(BigDecimal multaPercentual, BigDecimal jurosMensalPercentual, Boolean automatico) {

	public PropriedadesDoFaturamento {
		multaPercentual = multaPercentual == null ? new BigDecimal("2") : multaPercentual;
		jurosMensalPercentual = jurosMensalPercentual == null ? new BigDecimal("1") : jurosMensalPercentual;
		automatico = automatico == null || automatico;
	}

}
