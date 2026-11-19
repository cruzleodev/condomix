package br.com.condomix.financeiro.asaas;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Lê a mensagem de erro do Asaas: {@code {"errors": [{"code": "...", "description": "..."}]}} (já vem em português). */
final class ErrosDoAsaas {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private ErrosDoAsaas() {
	}

	static String primeiraDescricao(String corpo) {
		try {
			JsonNode erros = JSON.readTree(corpo).path("errors");
			return erros.isArray() && !erros.isEmpty() ? erros.get(0).path("description").asText(null) : null;
		}
		catch (RuntimeException e) {
			return null;
		}
	}

}
