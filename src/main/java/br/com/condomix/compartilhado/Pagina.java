package br.com.condomix.compartilhado;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Uma página de resultados, no formato devolvido pela API.
 *
 * @param pagina       número da página, começando em 0
 * @param tamanho      quantos itens por página
 * @param total        quantos itens existem no total
 * @param totalPaginas quantas páginas existem
 */
public record Pagina<T>(List<T> itens, int pagina, int tamanho, long total, int totalPaginas) {

	public static <E, T> Pagina<T> de(Page<E> pagina, Function<E, T> conversor) {
		return new Pagina<>(pagina.getContent().stream().map(conversor).toList(),
				pagina.getNumber(), pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
	}

}
