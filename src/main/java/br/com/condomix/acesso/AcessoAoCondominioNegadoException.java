package br.com.condomix.acesso;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** O usuário não tem perfil ativo no condomínio pedido. */
public class AcessoAoCondominioNegadoException extends ErrorResponseException {

	public AcessoAoCondominioNegadoException() {
		super(HttpStatus.FORBIDDEN, ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Você não tem acesso a este condomínio"), null);
	}

}
