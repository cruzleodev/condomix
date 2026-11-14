package br.com.condomix.compartilhado;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** 404. Também para registros de outro condomínio: não revelamos que existem. */
public class NaoEncontradoException extends ErrorResponseException {

	public NaoEncontradoException(String mensagem) {
		super(HttpStatus.NOT_FOUND, ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, mensagem), null);
	}

}
