package br.com.condomix.financeiro.dominio;

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
import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/** A geração da taxa de um mês, para todas as unidades. Só uma válida por mês. */
@Entity
@Table(name = "geracao_de_cobranca")
public class GeracaoDeCobranca {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@TenantId
	private UUID tenantId;

	/** Sempre o dia 1º do mês de referência. */
	private LocalDate competencia;

	@Enumerated(EnumType.STRING)
	private TipoDeRateio tipoRateio;

	private BigDecimal valorOrdinariaTotal;

	private UUID geradaPor;

	@CreationTimestamp
	private Instant geradaEm;

	private Instant canceladaEm;

	protected GeracaoDeCobranca() {
	}

	public GeracaoDeCobranca(UUID tenantId, YearMonth competencia, TipoDeRateio tipoRateio, BigDecimal valorOrdinariaTotal,
			UUID geradaPor) {
		this.tenantId = Objects.requireNonNull(tenantId);
		this.competencia = competencia.atDay(1);
		this.tipoRateio = Objects.requireNonNull(tipoRateio);
		this.valorOrdinariaTotal = valorOrdinariaTotal;
		this.geradaPor = geradaPor;
	}

	public void cancelar(Instant quando) {
		this.canceladaEm = quando;
	}

	public UUID getId() { return id; }
	public YearMonth getCompetencia() { return YearMonth.from(competencia); }
	public TipoDeRateio getTipoRateio() { return tipoRateio; }
	public BigDecimal getValorOrdinariaTotal() { return valorOrdinariaTotal; }
	public Instant getGeradaEm() { return geradaEm; }
	public Instant getCanceladaEm() { return canceladaEm; }

}
