package br.com.condomix.documentos.armazenamento;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste de contrato (SOLID: L). Toda implementação de {@link ArmazenamentoDeArquivos} cria uma
 * subclasse deste teste; se todas passam, qualquer uma pode substituir a outra sem surpresas.
 */
abstract class ContratoArmazenamentoDeArquivos {

	private ArmazenamentoDeArquivos armazenamento;

	protected abstract ArmazenamentoDeArquivos criar() throws Exception;

	@BeforeEach
	void preparar() throws Exception {
		armazenamento = criar();
	}

	@Test
	void guardaEDevolveOMesmoConteudo() throws IOException {
		byte[] conteudo = "conteúdo da ata".getBytes(StandardCharsets.UTF_8);

		long bytes = armazenamento.guardar("condominio-1/ata.pdf", new ByteArrayInputStream(conteudo));

		assertThat(bytes).isEqualTo(conteudo.length);
		assertThat(armazenamento.existe("condominio-1/ata.pdf")).isTrue();
		try (InputStream lido = armazenamento.abrir("condominio-1/ata.pdf")) {
			assertThat(lido.readAllBytes()).isEqualTo(conteudo);
		}
	}

	@Test
	void naoSobrescreveArquivoExistente() {
		armazenamento.guardar("a/b", new ByteArrayInputStream(new byte[] { 1 }));

		assertThatThrownBy(() -> armazenamento.guardar("a/b", new ByteArrayInputStream(new byte[] { 2 })))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void abrirChaveInexistenteLancaExcecaoPropria() {
		assertThatThrownBy(() -> armazenamento.abrir("nao/existe"))
				.isInstanceOf(ArmazenamentoDeArquivos.ArquivoInexistenteException.class);
		assertThat(armazenamento.existe("nao/existe")).isFalse();
	}

	@Test
	void removerApagaERemoverDeNovoNaoFalha() {
		armazenamento.guardar("x/y", new ByteArrayInputStream(new byte[] { 1 }));

		armazenamento.remover("x/y");
		armazenamento.remover("x/y");

		assertThat(armazenamento.existe("x/y")).isFalse();
	}

	@Test
	void recusaChaveQueTentaSairDaPasta() {
		assertThatThrownBy(() -> armazenamento.guardar("../fora.txt", new ByteArrayInputStream(new byte[] { 1 })))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
