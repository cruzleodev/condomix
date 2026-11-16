package br.com.condomix.acesso.convite;

import br.com.condomix.acesso.Perfil;
import br.com.condomix.acesso.Pessoa;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Convite para uma pessoa do condomínio ter acesso ao sistema. Vale até {@code expiraEm} e só pode
 * ser usado uma vez. O código em si nunca é gravado, só a impressão digital dele ({@link CodigoDeConvite}).
 */
@Entity
@Table(name = "convite")
public class Convite {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@TenantId
	private UUID tenantId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pessoa_id")
	private Pessoa pessoa;

	private String email;

	/** Papel que o convite dá; vazio quando o papel vem de um mandato (ex.: síndico). */
	@Enumerated(EnumType.STRING)
	private Perfil perfil;

	private String codigoHash;

	private Instant expiraEm;

	private Instant aceitoEm;

	private UUID aceitoPor;

	private Instant canceladoEm;

	private UUID criadoPor;

	@CreationTimestamp
	private Instant criadoEm;

	protected Convite() {
	}

	public Convite(Pessoa pessoa, String email, Perfil perfil, String codigoHash, Instant expiraEm, UUID criadoPor) {
		if (perfil != null && !perfil.gravadoComoPerfil()) {
			throw new IllegalArgumentException("O convite só pode dar os papéis morador, administradora ou portaria");
		}
		if (email == null || !email.contains("@")) {
			throw new IllegalArgumentException("Informe um e-mail válido");
		}
		this.tenantId = pessoa.getTenantId();
		this.pessoa = pessoa;
		this.email = email.strip().toLowerCase(Locale.ROOT);
		this.perfil = perfil;
		this.codigoHash = Objects.requireNonNull(codigoHash);
		this.expiraEm = Objects.requireNonNull(expiraEm);
		this.criadoPor = criadoPor;
	}

	public boolean podeSerUsadoEm(Instant momento) {
		return aceitoEm == null && canceladoEm == null && momento.isBefore(expiraEm);
	}

	public void aceitar(UUID usuarioId, Instant momento) {
		if (!podeSerUsadoEm(momento)) {
			throw new IllegalStateException("Este convite já foi usado, foi cancelado ou expirou");
		}
		this.aceitoPor = Objects.requireNonNull(usuarioId);
		this.aceitoEm = momento;
	}

	public void cancelar(Instant momento) {
		if (aceitoEm == null && canceladoEm == null) {
			this.canceladoEm = momento;
		}
	}

	public UUID getId() { return id; }
	public UUID getTenantId() { return tenantId; }
	public Pessoa getPessoa() { return pessoa; }
	public String getEmail() { return email; }
	public Perfil getPerfil() { return perfil; }
	public Instant getExpiraEm() { return expiraEm; }
	public Instant getAceitoEm() { return aceitoEm; }

}
