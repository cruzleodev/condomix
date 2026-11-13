package br.com.condomix.documentos.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Dados do arquivo de um documento. O conteúdo fica no armazenamento, na {@code chave}.
 *
 * @param sha256 impressão digital do conteúdo (64 caracteres hexadecimais)
 */
@Embeddable
public record ArquivoDoDocumento(
		@Column(name = "arquivo_nome") String nome,
		@Column(name = "arquivo_tipo") String tipoConteudo,
		@Column(name = "arquivo_tamanho") long tamanho,
		@Column(name = "arquivo_sha256") String sha256,
		@Column(name = "arquivo_chave") String chave) {
}
