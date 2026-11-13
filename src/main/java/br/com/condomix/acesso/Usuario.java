package br.com.condomix.acesso;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Login no Condomix. É global (sem tenant): um único e-mail pode acessar vários
 * condomínios, cada um com seus perfis (ver {@link PerfilAcesso}).
 */
@Entity
@Table(name = "usuario")
public class Usuario {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	private String email;

	/** Hash da senha (BCrypt). Nunca a senha em texto. */
	private String senhaHash;

	private boolean ativo = true;

	private Instant ultimoAcesso;

	@CreationTimestamp
	private Instant criadoEm;

	@UpdateTimestamp
	private Instant atualizadoEm;

	protected Usuario() {
	}

	public Usuario(String email, String senhaHash) {
		this.email = normalizar(email);
		this.senhaHash = Objects.requireNonNull(senhaHash);
	}

	static String normalizar(String email) {
		return Objects.requireNonNull(email).trim().toLowerCase(Locale.ROOT);
	}

	public void registrarAcesso(Instant quando) {
		this.ultimoAcesso = quando;
	}

	public void alterarSenha(String novoHash) {
		this.senhaHash = Objects.requireNonNull(novoHash);
	}

	public void desativar() {
		this.ativo = false;
	}

	public UUID getId() { return id; }
	public String getEmail() { return email; }
	public String getSenhaHash() { return senhaHash; }
	public boolean isAtivo() { return ativo; }
	public Instant getUltimoAcesso() { return ultimoAcesso; }
	public Instant getCriadoEm() { return criadoEm; }
	public Instant getAtualizadoEm() { return atualizadoEm; }

}
