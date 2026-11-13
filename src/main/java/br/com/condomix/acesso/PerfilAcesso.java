package br.com.condomix.acesso;

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
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** O que um usuário pode fazer em um condomínio (síndico, morador, portaria...). */
@Entity
@Table(name = "perfil_acesso")
public class PerfilAcesso {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	private UUID tenantId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id")
	private Usuario usuario;

	/** A pessoa que este login representa no condomínio. Opcional (ex.: administradora). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "pessoa_id")
	private Pessoa pessoa;

	@Enumerated(EnumType.STRING)
	private Perfil perfil;

	private boolean ativo = true;

	@CreationTimestamp
	private Instant criadoEm;

	protected PerfilAcesso() {
	}

	public PerfilAcesso(UUID tenantId, Usuario usuario, Perfil perfil, Pessoa pessoa) {
		if (pessoa != null && !pessoa.getTenantId().equals(tenantId)) {
			throw new IllegalArgumentException("A pessoa é de outro condomínio");
		}
		this.tenantId = Objects.requireNonNull(tenantId);
		this.usuario = Objects.requireNonNull(usuario);
		this.perfil = Objects.requireNonNull(perfil);
		this.pessoa = pessoa;
	}

	public void desativar() {
		this.ativo = false;
	}

	public UUID getId() { return id; }
	public UUID getTenantId() { return tenantId; }
	public Usuario getUsuario() { return usuario; }
	public Pessoa getPessoa() { return pessoa; }
	public Perfil getPerfil() { return perfil; }
	public boolean isAtivo() { return ativo; }
	public Instant getCriadoEm() { return criadoEm; }

}
