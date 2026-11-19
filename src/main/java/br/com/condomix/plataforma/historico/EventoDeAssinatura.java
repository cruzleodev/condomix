package br.com.condomix.plataforma.historico;

import br.com.condomix.plataforma.Periodicidade;
import br.com.condomix.plataforma.Plano;
import br.com.condomix.plataforma.StatusAssinatura;
import br.com.condomix.plataforma.TipoDeEventoDaAssinatura;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma mudança na assinatura de um cliente, para o histórico do painel da plataforma.
 * Sem {@code @TenantId}: é um dado da plataforma, lido pelo administrador para todos os condomínios.
 */
@Entity
@Table(name = "evento_assinatura")
public class EventoDeAssinatura {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	private UUID condominioId;

	private Instant momento;

	@Enumerated(EnumType.STRING)
	private TipoDeEventoDaAssinatura tipo;

	/** A assinatura depois da mudança. Vazios na suspensão e na reativação do condomínio. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "plano_id")
	private Plano plano;

	@Enumerated(EnumType.STRING)
	private Periodicidade periodicidade;

	@Enumerated(EnumType.STRING)
	private StatusAssinatura status;

	/** Quem fez; vazio quando foi o sistema. */
	private UUID feitoPor;

	protected EventoDeAssinatura() {
	}

	private EventoDeAssinatura(UUID condominioId, Instant momento, TipoDeEventoDaAssinatura tipo, Plano plano,
			Periodicidade periodicidade, StatusAssinatura status, UUID feitoPor) {
		this.condominioId = Objects.requireNonNull(condominioId);
		this.momento = Objects.requireNonNull(momento);
		this.tipo = Objects.requireNonNull(tipo);
		this.plano = plano;
		this.periodicidade = periodicidade;
		this.status = status;
		this.feitoPor = feitoPor;
	}

	/** Cliente novo, troca de plano ou mudança de situação: guarda como a assinatura ficou. */
	public static EventoDeAssinatura daAssinatura(UUID condominioId, Instant momento, TipoDeEventoDaAssinatura tipo,
			Plano plano, Periodicidade periodicidade, StatusAssinatura status, UUID feitoPor) {
		return new EventoDeAssinatura(condominioId, momento, tipo, Objects.requireNonNull(plano),
				Objects.requireNonNull(periodicidade), Objects.requireNonNull(status), feitoPor);
	}

	/** Suspensão ou reativação do condomínio. */
	public static EventoDeAssinatura doAcesso(UUID condominioId, Instant momento, boolean suspenso, UUID feitoPor) {
		return new EventoDeAssinatura(condominioId, momento,
				suspenso ? TipoDeEventoDaAssinatura.SUSPENSAO : TipoDeEventoDaAssinatura.REATIVACAO, null, null, null, feitoPor);
	}

	public UUID getId() { return id; }
	public UUID getCondominioId() { return condominioId; }
	public Instant getMomento() { return momento; }
	public TipoDeEventoDaAssinatura getTipo() { return tipo; }
	public Plano getPlano() { return plano; }
	public Periodicidade getPeriodicidade() { return periodicidade; }
	public StatusAssinatura getStatus() { return status; }
	public UUID getFeitoPor() { return feitoPor; }

}
