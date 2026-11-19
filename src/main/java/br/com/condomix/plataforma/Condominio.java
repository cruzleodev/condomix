package br.com.condomix.plataforma;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * O condomínio é o tenant: cada cliente do Condomix. Seu id é o {@code tenant_id}
 * gravado nas tabelas dos outros módulos.
 */
@Entity
@Table(name = "condominio")
public class Condominio {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	private String nome;

	private String cnpj;

	@Enumerated(EnumType.STRING)
	private TipoCondominio tipo;

	private String email;

	private String telefone;

	@Embedded
	private Endereco endereco;

	private boolean ativo = true;

	/** Vazio enquanto o condomínio está ativo. */
	@Enumerated(EnumType.STRING)
	private MotivoDaSuspensao motivoSuspensao;

	@CreationTimestamp
	private Instant criadoEm;

	@UpdateTimestamp
	private Instant atualizadoEm;

	protected Condominio() {
	}

	public Condominio(String nome, TipoCondominio tipo) {
		this.nome = Objects.requireNonNull(nome);
		this.tipo = Objects.requireNonNull(tipo);
	}

	public void alterarContato(String email, String telefone) {
		this.email = email;
		this.telefone = telefone;
	}

	public void alterarEndereco(Endereco endereco) {
		this.endereco = endereco;
	}

	public void definirCnpj(String cnpj) {
		this.cnpj = cnpj;
	}

	/**
	 * Suspende o condomínio: ninguém dele consegue mais entrar no sistema (na falta de pagamento, a gestão ainda
	 * entra, só para pagar a fatura). Suspender à mão um condomínio já suspenso por falta de pagamento troca o
	 * motivo: aí ele só volta quando o administrador reativar.
	 */
	public void suspender(MotivoDaSuspensao motivo) {
		this.ativo = false;
		this.motivoSuspensao = Objects.requireNonNull(motivo);
	}

	public void reativar() {
		this.ativo = true;
		this.motivoSuspensao = null;
	}

	public boolean isSuspensoPorFaltaDePagamento() {
		return motivoSuspensao == MotivoDaSuspensao.FALTA_DE_PAGAMENTO;
	}

	public UUID getId() { return id; }
	public String getNome() { return nome; }
	public String getCnpj() { return cnpj; }
	public TipoCondominio getTipo() { return tipo; }
	public String getEmail() { return email; }
	public String getTelefone() { return telefone; }
	public Endereco getEndereco() { return endereco; }
	public boolean isAtivo() { return ativo; }
	public MotivoDaSuspensao getMotivoSuspensao() { return motivoSuspensao; }
	public Instant getCriadoEm() { return criadoEm; }
	public Instant getAtualizadoEm() { return atualizadoEm; }

}
