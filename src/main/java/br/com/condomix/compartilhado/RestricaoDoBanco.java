package br.com.condomix.compartilhado;

import org.springframework.dao.DataIntegrityViolationException;

/** Descobre qual regra do banco (restrição) foi violada, para devolver uma mensagem clara ao usuário. */
public final class RestricaoDoBanco {

	private RestricaoDoBanco() {
	}

	public static boolean violou(DataIntegrityViolationException erro, String nomeDaRestricao) {
		return String.valueOf(erro.getMostSpecificCause().getMessage()).contains(nomeDaRestricao);
	}

}
