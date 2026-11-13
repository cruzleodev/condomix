package br.com.condomix.documentos.dominio;

import br.com.condomix.documentos.Categoria;

import java.time.LocalDate;
import java.util.Objects;

/** Os dados que o síndico preenche ao publicar ou editar um documento. */
public record DadosDoDocumento(String titulo, Categoria categoria, String descricao, LocalDate dataReferencia) {

	public DadosDoDocumento {
		if (titulo == null || titulo.isBlank()) {
			throw new IllegalArgumentException("O título é obrigatório");
		}
		titulo = titulo.strip();
		Objects.requireNonNull(categoria, "A categoria é obrigatória");
		descricao = descricao == null || descricao.isBlank() ? null : descricao.strip();
	}

}
