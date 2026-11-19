package br.com.condomix.comunicacao.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma notificação para uma pessoa, num condomínio: aparece no sino do app e pode ir também por e-mail e push.
 * A {@code chave} identifica o acontecimento: o mesmo acontecimento não notifica a mesma pessoa duas vezes
 * (eventos reprocessados, lembretes que rodam de novo).
 */
@Entity
@Table(name = "notificacao")
public class Notificacao {

	private static final int TAMANHO_DO_TEXTO = 1000;

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@TenantId
	private UUID tenantId;

	private UUID usuarioId;

	@Enumerated(EnumType.STRING)
	private GrupoDeNotificacao grupo;

	private String chave;

	private String titulo;

	private String texto;

	private String link;

	@CreationTimestamp
	private Instant criadoEm;

	private Instant lidoEm;

	protected Notificacao() {
	}

	public Notificacao(UUID tenantId, UUID usuarioId, GrupoDeNotificacao grupo, String chave, String titulo, String texto,
			String link) {
		this.tenantId = Objects.requireNonNull(tenantId);
		this.usuarioId = Objects.requireNonNull(usuarioId);
		this.grupo = Objects.requireNonNull(grupo);
		this.chave = Objects.requireNonNull(chave);
		this.titulo = Objects.requireNonNull(titulo);
		this.texto = texto.length() > TAMANHO_DO_TEXTO ? texto.substring(0, TAMANHO_DO_TEXTO) : texto;
		this.link = link;
	}

	public void marcarLida(Instant quando) {
		if (lidoEm == null) {
			lidoEm = quando;
		}
	}

	public UUID getId() { return id; }
	public UUID getTenantId() { return tenantId; }
	public UUID getUsuarioId() { return usuarioId; }
	public GrupoDeNotificacao getGrupo() { return grupo; }
	public String getChave() { return chave; }
	public String getTitulo() { return titulo; }
	public String getTexto() { return texto; }
	public String getLink() { return link; }
	public Instant getCriadoEm() { return criadoEm; }
	public Instant getLidoEm() { return lidoEm; }

}
