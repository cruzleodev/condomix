package br.com.condomix.documentos.web;

import br.com.condomix.documentos.Categoria;
import br.com.condomix.documentos.dominio.Documento;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Um documento como a API devolve (sem a chave interna do armazenamento). */
record DocumentoResposta(
		UUID id,
		String titulo,
		String descricao,
		Categoria categoria,
		String categoriaRotulo,
		LocalDate dataReferencia,
		Arquivo arquivo,
		Instant enviadoEm) {

	record Arquivo(String nome, String tipo, long tamanho, String sha256) {
	}

	static DocumentoResposta de(Documento d) {
		var a = d.getArquivo();
		return new DocumentoResposta(d.getId(), d.getTitulo(), d.getDescricao(), d.getCategoria(),
				d.getCategoria().rotulo(), d.getDataReferencia(),
				new Arquivo(a.nome(), a.tipoConteudo(), a.tamanho(), a.sha256()), d.getEnviadoEm());
	}

}
