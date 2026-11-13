package br.com.condomix.plataforma;

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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Assinatura de um plano por um condomínio. */
@Entity
@Table(name = "assinatura")
public class Assinatura {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	private UUID tenantId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "plano_id")
	private Plano plano;

	@Enumerated(EnumType.STRING)
	private Periodicidade periodicidade;

	@Enumerated(EnumType.STRING)
	private StatusAssinatura status;

	private LocalDate inicio;

	private LocalDate fim;

	@CreationTimestamp
	private Instant criadoEm;

	@UpdateTimestamp
	private Instant atualizadoEm;

	protected Assinatura() {
	}

	public Assinatura(Condominio condominio, Plano plano, Periodicidade periodicidade, StatusAssinatura status, LocalDate inicio) {
		this.tenantId = Objects.requireNonNull(condominio.getId(), "o condomínio precisa estar salvo");
		this.plano = Objects.requireNonNull(plano);
		this.periodicidade = Objects.requireNonNull(periodicidade);
		this.status = Objects.requireNonNull(status);
		this.inicio = Objects.requireNonNull(inicio);
	}

	public void cancelar(LocalDate fim) {
		this.status = StatusAssinatura.CANCELADA;
		this.fim = fim;
	}

	public UUID getId() { return id; }
	public UUID getTenantId() { return tenantId; }
	public Plano getPlano() { return plano; }
	public Periodicidade getPeriodicidade() { return periodicidade; }
	public StatusAssinatura getStatus() { return status; }
	public LocalDate getInicio() { return inicio; }
	public LocalDate getFim() { return fim; }
	public Instant getCriadoEm() { return criadoEm; }
	public Instant getAtualizadoEm() { return atualizadoEm; }

}
