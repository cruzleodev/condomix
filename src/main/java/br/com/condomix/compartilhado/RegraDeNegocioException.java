package br.com.condomix.compartilhado;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** 400 (pedido viola uma regra) ou 409 (conflita com o que já existe), com a explicação para o usuário. */
public class RegraDeNegocioException extends ErrorResponseException {

	private RegraDeNegocioException(HttpStatus status, String mensagem) {
		super(status, ProblemDetail.forStatusAndDetail(status, mensagem), null);
	}

	public static RegraDeNegocioException invalido(String mensagem) {
		return new RegraDeNegocioException(HttpStatus.BAD_REQUEST, mensagem);
	}

	public static RegraDeNegocioException conflito(String mensagem) {
		return new RegraDeNegocioException(HttpStatus.CONFLICT, mensagem);
	}

}
