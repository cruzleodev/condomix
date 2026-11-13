package br.com.condomix.documentos.armazenamento;

import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Onde os arquivos ficam guardados. O resto do sistema só conhece esta interface (SOLID: D e O):
 * em desenvolvimento os arquivos vão para o disco; em produção, para um serviço como o S3.
 *
 * <p>Contrato (verificado por {@code ContratoArmazenamentoDeArquivos}, que toda implementação deve passar):
 * <ul>
 *   <li>a chave é definida por quem chama e pode ter "/" para organizar em pastas;</li>
 *   <li>guardar numa chave que já existe é erro (nada é sobrescrito sem querer);</li>
 *   <li>abrir uma chave inexistente lança {@link ArquivoInexistenteException};</li>
 *   <li>remover uma chave inexistente não faz nada.</li>
 * </ul>
 * Falhas de leitura e escrita viram {@link UncheckedIOException}.
 */
public interface ArmazenamentoDeArquivos {

	/** Guarda o conteúdo e devolve quantos bytes foram gravados. Não fecha o {@code conteudo}. */
	long guardar(String chave, InputStream conteudo);

	/** Abre o arquivo para leitura. Quem chama deve fechar o stream. */
	InputStream abrir(String chave);

	boolean existe(String chave);

	void remover(String chave);

	class ArquivoInexistenteException extends RuntimeException {

		public ArquivoInexistenteException(String chave) {
			super("Arquivo não encontrado no armazenamento: " + chave);
		}

	}

}
