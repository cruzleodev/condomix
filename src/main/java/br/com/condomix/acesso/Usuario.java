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

	/** Administrador do Condomix (ver {@link Perfil#ADMIN_PLATAFORMA}). */
	private boolean adminPlataforma;

	private Instant ultimoAcesso;

	/** Bloqueio pelo administrador da plataforma. Bloqueado, o usuário fica inativo. */
	private Instant bloqueadoEm;

	private String motivoBloqueio;

	private UUID bloqueadoPor;

	@CreationTimestamp
	private Instant criadoEm;

	@UpdateTimestamp
	private Instant atualizadoEm;

	protected Usuario() {
	}

	public Usuario(String email, String senhaHash) {
		this.email = normalizarEmail(email);
		this.senhaHash = Objects.requireNonNull(senhaHash);
	}

	public static String normalizarEmail(String email) {
		return Objects.requireNonNull(email).trim().toLowerCase(Locale.ROOT);
	}

	public void registrarAcesso(Instant quando) {
		this.ultimoAcesso = quando;
	}

	public void alterarSenha(String novoHash) {
		this.senhaHash = Objects.requireNonNull(novoHash);
	}

	public void tornarAdministradorDaPlataforma() {
		this.adminPlataforma = true;
	}

	public void desativar() {
		this.ativo = false;
	}

	/** O login deixa de entrar e os tokens que ele já tem deixam de valer (ver {@code ValidadorDeSessao}). */
	public void bloquear(String motivo, UUID por, Instant quando) {
		if (motivo == null || motivo.strip().length() < 10) {
			throw new IllegalArgumentException("Explique o motivo do bloqueio (pelo menos 10 caracteres)");
		}
		if (Objects.equals(por, id)) {
			throw new IllegalArgumentException("Você não pode bloquear o seu próprio login");
		}
		this.ativo = false;
		this.motivoBloqueio = motivo.strip();
		this.bloqueadoPor = por;
		this.bloqueadoEm = Objects.requireNonNull(quando);
	}

	public void desbloquear() {
		if (bloqueadoEm == null) {
			throw new IllegalStateException("Este login não está bloqueado");
		}
		this.ativo = true;
		this.motivoBloqueio = null;
		this.bloqueadoPor = null;
		this.bloqueadoEm = null;
	}

	public UUID getId() { return id; }
	public String getEmail() { return email; }
	public String getSenhaHash() { return senhaHash; }
	public boolean isAtivo() { return ativo; }
	public boolean isAdminPlataforma() { return adminPlataforma; }
	public Instant getUltimoAcesso() { return ultimoAcesso; }
	public Instant getBloqueadoEm() { return bloqueadoEm; }
	public String getMotivoBloqueio() { return motivoBloqueio; }
	public boolean isBloqueado() { return bloqueadoEm != null; }
	public Instant getCriadoEm() { return criadoEm; }
	public Instant getAtualizadoEm() { return atualizadoEm; }

}
