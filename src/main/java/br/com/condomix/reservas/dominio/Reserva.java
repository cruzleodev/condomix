package br.com.condomix.reservas.dominio;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/**
 * A reserva de um horário de um espaço, num dia, para uma unidade. Guarda o horário e o valor de quando foi feita.
 * Quando o espaço cobra aluguel, aponta para a cobrança avulsa da unidade.
 */
@Entity
@Table(name = "reserva")
public class Reserva {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@TenantId
	private UUID tenantId;

	private UUID espacoId;

	private UUID unidadeId;

	private LocalDate data;

	private String periodo;

	@Convert(converter = HoraDoDia.class)
	private LocalTime inicio;

	@Convert(converter = HoraDoDia.class)
	private LocalTime fim;

	@Enumerated(EnumType.STRING)
	private SituacaoDaReserva situacao;

	private BigDecimal valor;

	private UUID cobrancaId;

	private String observacao;

	private UUID reservadaPor;

	@CreationTimestamp
	private Instant criadoEm;

	private Instant canceladaEm;

	private UUID canceladaPor;

	private String motivoCancelamento;

	private boolean aluguelMantido;

	protected Reserva() {
	}

	public Reserva(Espaco espaco, PeriodoDoEspaco periodo, UUID unidadeId, LocalDate data, String observacao, UUID reservadaPor) {
		this.tenantId = espaco.getTenantId();
		this.espacoId = Objects.requireNonNull(espaco.getId());
		this.unidadeId = Objects.requireNonNull(unidadeId);
		this.data = Objects.requireNonNull(data);
		this.periodo = periodo.nome();
		this.inicio = periodo.inicio();
		this.fim = periodo.fim();
		this.valor = espaco.getValorAluguel();
		this.observacao = observacao == null || observacao.isBlank() ? null : observacao.strip();
		this.reservadaPor = reservadaPor;
		this.situacao = SituacaoDaReserva.CONFIRMADA;
	}

	public void cobradaPor(UUID cobrancaId) {
		this.cobrancaId = Objects.requireNonNull(cobrancaId);
	}

	/**
	 * @param aluguelMantido cancelou depois do prazo: a cobrança do aluguel continua
	 */
	public void cancelar(Instant quando, UUID por, String motivo, boolean aluguelMantido) {
		if (situacao == SituacaoDaReserva.CANCELADA) {
			throw new IllegalStateException("Esta reserva já foi cancelada");
		}
		this.situacao = SituacaoDaReserva.CANCELADA;
		this.canceladaEm = Objects.requireNonNull(quando);
		this.canceladaPor = por;
		this.motivoCancelamento = motivo == null || motivo.isBlank() ? null : motivo.strip();
		this.aluguelMantido = aluguelMantido;
	}

	/** Último dia para cancelar sem custo. */
	public LocalDate cancelavelSemCustoAte(int diasParaCancelar) {
		return data.minusDays(diasParaCancelar);
	}

	public boolean sobrepoe(LocalTime outroInicio, LocalTime outroFim) {
		return inicio.isBefore(outroFim) && outroInicio.isBefore(fim);
	}

	public UUID getId() { return id; }
	public UUID getTenantId() { return tenantId; }
	public UUID getEspacoId() { return espacoId; }
	public UUID getUnidadeId() { return unidadeId; }
	public LocalDate getData() { return data; }
	public String getPeriodo() { return periodo; }
	public LocalTime getInicio() { return inicio; }
	public LocalTime getFim() { return fim; }
	public SituacaoDaReserva getSituacao() { return situacao; }
	public BigDecimal getValor() { return valor; }
	public UUID getCobrancaId() { return cobrancaId; }
	public String getObservacao() { return observacao; }
	public UUID getReservadaPor() { return reservadaPor; }
	public Instant getCriadoEm() { return criadoEm; }
	public Instant getCanceladaEm() { return canceladaEm; }
	public String getMotivoCancelamento() { return motivoCancelamento; }
	public boolean isAluguelMantido() { return aluguelMantido; }

}
