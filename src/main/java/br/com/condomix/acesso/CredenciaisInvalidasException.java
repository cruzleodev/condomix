package br.com.condomix.acesso;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** E-mail ou senha errados, ou usuário desativado. A mensagem não diz qual dos dois, de propósito. */
public class CredenciaisInvalidasException extends ErrorResponseException {

	public CredenciaisInvalidasException() {
		super(HttpStatus.UNAUTHORIZED, ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos"), null);
	}

}
