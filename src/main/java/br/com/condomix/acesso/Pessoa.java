package br.com.condomix.acesso;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
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
		this.nome = Objects.requireNonNull(nome);
	}

	public void definirDocumento(String documento) {
		this.documento = documento;
	}

	public void alterarContato(String email, String telefone) {
		this.email = email;
		this.telefone = telefone;
	}

	public UUID getId() { return id; }
	public UUID getTenantId() { return tenantId; }
	public String getNome() { return nome; }
	public String getDocumento() { return documento; }
	public String getEmail() { return email; }
	public String getTelefone() { return telefone; }
	public Instant getCriadoEm() { return criadoEm; }
	public Instant getAtualizadoEm() { return atualizadoEm; }

}
