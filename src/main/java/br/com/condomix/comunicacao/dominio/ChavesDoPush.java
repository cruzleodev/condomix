package br.com.condomix.comunicacao.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/** O par de chaves VAPID (identifica o Condomix nos serviços de push). Uma linha só; a privada fica cifrada. */
@Entity
@Table(name = "chaves_do_push")
public class ChavesDoPush {

	@Id
	private Integer id = 1;

	/** Ponto público P-256 sem compressão, em Base64 URL (é o que o navegador pede). */
	private String chavePublica;

	/** A chave privada PKCS#8, cifrada pelo Cofre. */
	private String chavePrivada;

	protected ChavesDoPush() {
	}

	public ChavesDoPush(String chavePublica, String chavePrivadaCifrada) {
		this.chavePublica = Objects.requireNonNull(chavePublica);
		this.chavePrivada = Objects.requireNonNull(chavePrivadaCifrada);
	}

	public String getChavePublica() { return chavePublica; }
	public String getChavePrivada() { return chavePrivada; }

}
