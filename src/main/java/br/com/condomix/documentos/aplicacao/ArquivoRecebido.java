package br.com.condomix.documentos.aplicacao;

import java.io.IOException;
import java.io.InputStream;

/**
 * Um arquivo enviado, sem amarrar os casos de uso ao HTTP (SOLID: D). O controller adapta o
 * {@code MultipartFile}; um teste ou o bot do WhatsApp podem criar o seu.
 *
 * @param conteudo abre o conteúdo; pode ser chamado mais de uma vez
 */
public record ArquivoRecebido(String nome, FonteDeConteudo conteudo) {

	@FunctionalInterface
	public interface FonteDeConteudo {
		InputStream abrir() throws IOException;
	}

}
