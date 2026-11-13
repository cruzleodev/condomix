package br.com.condomix.documentos.aplicacao;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Também usado quando o documento existe em outro condomínio: não revelamos que ele existe. */
public class DocumentoNaoEncontradoException extends ErrorResponseException {

	public DocumentoNaoEncontradoException() {
		super(HttpStatus.NOT_FOUND, ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Documento não encontrado"), null);
	}

}
