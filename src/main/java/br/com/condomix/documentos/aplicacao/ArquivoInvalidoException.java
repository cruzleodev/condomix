package br.com.condomix.documentos.aplicacao;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class ArquivoInvalidoException extends ErrorResponseException {

	public ArquivoInvalidoException(String motivo) {
		super(HttpStatus.BAD_REQUEST, ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, motivo), null);
	}

}
