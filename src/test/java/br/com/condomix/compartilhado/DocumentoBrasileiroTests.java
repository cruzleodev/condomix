package br.com.condomix.compartilhado;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentoBrasileiroTests {

	@Test
	void cpf() {
		assertThat(DocumentoBrasileiro.normalizar("529.982.247-25")).isEqualTo("52998224725");
		assertThatThrownBy(() -> DocumentoBrasileiro.normalizar("529.982.247-24")).hasMessage("CPF inválido");
		assertThatThrownBy(() -> DocumentoBrasileiro.normalizar("111.111.111-11")).hasMessage("CPF inválido");
	}

	@Test
	void cnpjNumerico() {
		assertThat(DocumentoBrasileiro.normalizar("11.222.333/0001-81")).isEqualTo("11222333000181");
		assertThatThrownBy(() -> DocumentoBrasileiro.normalizar("11.222.333/0001-82")).hasMessage("CNPJ inválido");
	}

	@Test
	void cnpjAlfanumericoDaReceita() {
		// Exemplo oficial da Receita Federal para o CNPJ alfanumérico (julho de 2026).
		assertThat(DocumentoBrasileiro.normalizar("12.abc.345/01de-35")).isEqualTo("12ABC34501DE35");
		assertThat(DocumentoBrasileiro.ehCnpj("12ABC34501DE35")).isTrue();
		assertThatThrownBy(() -> DocumentoBrasileiro.normalizar("12.ABC.345/01DE-36")).hasMessage("CNPJ inválido");
	}

	@Test
	void vazioEhOpcionalEFormatoErradoExplicaOProblema() {
		assertThat(DocumentoBrasileiro.normalizar("  ")).isNull();
		assertThatThrownBy(() -> DocumentoBrasileiro.normalizar("123")).hasMessageContaining("CPF (11 dígitos)");
	}

	@Test
	void telefone() {
		assertThat(TelefoneBrasileiro.normalizar("(91) 99999-8888")).isEqualTo("5591999998888");
		assertThat(TelefoneBrasileiro.normalizar("+55 91 3222-1111")).isEqualTo("559132221111");
		assertThatThrownBy(() -> TelefoneBrasileiro.normalizar("9999-8888")).isInstanceOf(IllegalArgumentException.class);
	}

}
