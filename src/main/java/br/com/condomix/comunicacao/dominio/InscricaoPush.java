package br.com.condomix.comunicacao.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Um aparelho (navegador ou app instalado) em que a pessoa ligou o push. Os dados vêm do navegador
 * ({@code PushSubscription}): o endereço do serviço de push e as chaves para cifrar a mensagem.
 */
@Entity
@Table(name = "inscricao_push")
public class InscricaoPush {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	private UUID usuarioId;

	private String endpoint;

	private String p256dh;

	private String auth;

	@CreationTimestamp
	private Instant criadaEm;

	protected InscricaoPush() {
	}

	public InscricaoPush(UUID usuarioId, String endpoint, String p256dh, String auth) {
		this.usuarioId = Objects.requireNonNull(usuarioId);
		this.endpoint = Objects.requireNonNull(endpoint);
		this.p256dh = Objects.requireNonNull(p256dh);
		this.auth = Objects.requireNonNull(auth);
	}

	/** O mesmo aparelho, inscrito de novo (ex.: outra pessoa entrou nele). */
	public void renovar(UUID usuarioId, String p256dh, String auth) {
		this.usuarioId = Objects.requireNonNull(usuarioId);
		this.p256dh = Objects.requireNonNull(p256dh);
		this.auth = Objects.requireNonNull(auth);
	}

	public UUID getId() { return id; }
	public UUID getUsuarioId() { return usuarioId; }
	public String getEndpoint() { return endpoint; }
	public String getP256dh() { return p256dh; }
	public String getAuth() { return auth; }

}
