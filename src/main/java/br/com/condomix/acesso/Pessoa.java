package br.com.condomix.acesso;

import br.com.condomix.compartilhado.DocumentoBrasileiro;
import br.com.condomix.compartilhado.TelefoneBrasileiro;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Pessoa ligada a um condomínio: proprietário, inquilino, morador, síndico...
 * Pode ou não ter login (ver {@link Usuario} e {@link PerfilAcesso}).
 */
@Entity
@Table(name = "pessoa")
public class Pessoa {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@TenantId
	private UUID tenantId;

	private String nome;

	/** CPF ou CNPJ, só dígitos. */
	private String documento;

	private String email;

	/** O login desta pessoa no sistema; vazio enquanto ela não tiver acesso. */
	private UUID usuarioId;

	/** Formato E.164 (ex.: 5591999999999); usado no WhatsApp. */
	private String telefone;

	@CreationTimestamp
	private Instant criadoEm;

	@UpdateTimestamp
	private Instant atualizadoEm;

	protected Pessoa() {
	}

	public Pessoa(UUID tenantId, String nome) {
		this.tenantId = Objects.requireNonNull(tenantId);
		alterarNome(nome);
	}

	public void alterarNome(String nome) {
		if (nome == null || nome.isBlank()) {
			throw new IllegalArgumentException("Informe o nome");
		}
		this.nome = nome.strip();
	}

	/** CPF ou CNPJ (inclusive alfanumérico), com ou sem pontuação. Inválido lança IllegalArgumentException. */
	public void definirDocumento(String documento) {
		this.documento = DocumentoBrasileiro.normalizar(documento);
	}

	public void vincularUsuario(UUID usuarioId) {
		this.usuarioId = Objects.requireNonNull(usuarioId);
	}

	/** O login da pessoa foi excluído: o cadastro fica no condomínio, sem acesso ao app. */
	public void desvincularUsuario() {
		this.usuarioId = null;
	}

	public void alterarContato(String email, String telefone) {
		if (email != null && !email.isBlank() && !email.contains("@")) {
			throw new IllegalArgumentException("E-mail inválido");
		}
		this.email = email == null || email.isBlank() ? null : email.strip().toLowerCase(Locale.ROOT);
		this.telefone = TelefoneBrasileiro.normalizar(telefone);
	}

	public UUID getId() { return id; }
	public UUID getTenantId() { return tenantId; }
	public String getNome() { return nome; }
	public String getDocumento() { return documento; }
	public String getEmail() { return email; }
	public String getTelefone() { return telefone; }
	public UUID getUsuarioId() { return usuarioId; }
	public Instant getCriadoEm() { return criadoEm; }
	public Instant getAtualizadoEm() { return atualizadoEm; }

}
