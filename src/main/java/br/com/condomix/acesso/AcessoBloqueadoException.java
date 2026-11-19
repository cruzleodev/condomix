package br.com.condomix.acesso;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Login bloqueado pelo administrador. Só é informado a quem acertou a senha. */
public class AcessoBloqueadoException extends ErrorResponseException {

	public AcessoBloqueadoException() {
		super(HttpStatus.FORBIDDEN, ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
				"Seu acesso está bloqueado. Fale com o suporte do Condomix."), null);
	}

}
