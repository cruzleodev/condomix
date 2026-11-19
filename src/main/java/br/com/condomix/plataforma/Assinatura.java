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
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Assinatura de um plano por um condomínio. Em {@code TESTE}, o condomínio não paga; ao fim do teste
 * ({@code fimDoTeste}), ela vira {@code ATIVA} sozinha e começa a ser faturada.
 */
@Entity
@Table(name = "assinatura")
public class Assinatura {

	/** Duração do teste grátis de um cliente novo. */
	public static final int DIAS_DE_TESTE = 30;

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@TenantId
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

	/** Só no teste: quando ele acaba. Vazio = teste sem prazo. */
	private LocalDate fimDoTeste;

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
		if (status == StatusAssinatura.TESTE) {
			this.fimDoTeste = inicio.plusDays(DIAS_DE_TESTE);
		}
	}

	public void cancelar(LocalDate fim) {
		this.status = StatusAssinatura.CANCELADA;
		this.fim = fim;
		this.fimDoTeste = null;
	}

	/**
	 * Muda a situação (teste, ativa, suspensa). Para cancelar, use {@link #cancelar}. Voltar ao teste dá
	 * mais {@value #DIAS_DE_TESTE} dias a partir de {@code hoje}.
	 */
	public void alterarStatus(StatusAssinatura novo, LocalDate hoje) {
		if (novo == StatusAssinatura.CANCELADA) {
			throw new IllegalArgumentException("Para cancelar, use cancelar(fim)");
		}
		if (this.status == StatusAssinatura.CANCELADA) {
			throw new IllegalStateException("Assinatura cancelada não muda de situação");
		}
		if (novo == StatusAssinatura.TESTE && this.status != StatusAssinatura.TESTE) {
			this.fimDoTeste = hoje.plusDays(DIAS_DE_TESTE);
		}
		if (novo != StatusAssinatura.TESTE) {
			this.fimDoTeste = null;
		}
		this.status = novo;
	}

	/** Muda o fim do teste grátis; vazio = sem prazo. Só vale para assinatura em teste. */
	public void definirFimDoTeste(LocalDate fim) {
		if (status != StatusAssinatura.TESTE) {
			throw new IllegalStateException("Só a assinatura em teste tem prazo de teste");
		}
		this.fimDoTeste = fim;
	}

	/** O teste acabou ({@code fimDoTeste} chegou): vira ativa e passa a ser faturada. */
	public boolean encerrarTesteSeVenceu(LocalDate hoje) {
		if (status != StatusAssinatura.TESTE || fimDoTeste == null || fimDoTeste.isAfter(hoje)) {
			return false;
		}
		alterarStatus(StatusAssinatura.ATIVA, hoje);
		return true;
	}

	public boolean isVigente() {
		return status != StatusAssinatura.CANCELADA;
	}

	public UUID getId() { return id; }
	public UUID getTenantId() { return tenantId; }
	public Plano getPlano() { return plano; }
	public Periodicidade getPeriodicidade() { return periodicidade; }
	public StatusAssinatura getStatus() { return status; }
	public LocalDate getInicio() { return inicio; }
	public LocalDate getFim() { return fim; }
	public LocalDate getFimDoTeste() { return fimDoTeste; }
	public Instant getCriadoEm() { return criadoEm; }
	public Instant getAtualizadoEm() { return atualizadoEm; }

}
