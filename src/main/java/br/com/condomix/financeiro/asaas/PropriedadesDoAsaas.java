package br.com.condomix.financeiro.asaas;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Endereços da API do Asaas e limites de tempo das chamadas. */
@ConfigurationProperties("condomix.gateway.asaas")
public record PropriedadesDoAsaas(String urlSandbox, String urlProducao, Duration tempoDeConexao, Duration tempoDeResposta) {

	public PropriedadesDoAsaas {
		urlSandbox = urlSandbox == null ? "https://api-sandbox.asaas.com/v3" : urlSandbox;
		urlProducao = urlProducao == null ? "https://api.asaas.com/v3" : urlProducao;
		tempoDeConexao = tempoDeConexao == null ? Duration.ofSeconds(10) : tempoDeConexao;
		tempoDeResposta = tempoDeResposta == null ? Duration.ofSeconds(30) : tempoDeResposta;
	}

}
