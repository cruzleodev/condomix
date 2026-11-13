package br.com.condomix.plataforma;

import jakarta.persistence.Embeddable;

@Embeddable
public record Endereco(
		String cep,
		String logradouro,
		String numero,
		String complemento,
		String bairro,
		String cidade,
		String uf) {
}
