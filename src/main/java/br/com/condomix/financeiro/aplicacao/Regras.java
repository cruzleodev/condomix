package br.com.condomix.financeiro.aplicacao;

import br.com.condomix.compartilhado.RegraDeNegocioException;

import java.util.function.Supplier;

/** Transforma as recusas das entidades (IllegalArgument/IllegalState) em respostas 400 com a explicação. */
final class Regras {

	private Regras() {
	}

	static <T> T aplicar(Supplier<T> acao) {
		try {
			return acao.get();
		}
		catch (IllegalArgumentException | IllegalStateException | NullPointerException e) {
			throw RegraDeNegocioException.invalido(e.getMessage());
		}
	}

	static void aplicar(Runnable acao) {
		aplicar(() -> {
			acao.run();
			return null;
		});
	}

}
