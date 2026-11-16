package br.com.condomix.financeiro.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Como o condomínio cobra a taxa: rateio, vencimento, multa, juros e fundo de reserva. */
@Entity
@Table(name = "configuracao_cobranca")
public class ConfiguracaoDaCobranca {

	/** Código Civil, art. 1.336, § 1º: multa de até 2%. */
	public static final BigDecimal MULTA_MAXIMA = BigDecimal.valueOf(2);

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@TenantId
	private UUID tenantId;

	@Enumerated(EnumType.STRING)
	private TipoDeRateio tipoRateio;

	private short diaVencimento;

	private BigDecimal multaPercentual;

	private BigDecimal jurosMensalPercentual;

	private BigDecimal fundoReservaPercentual;

	@UpdateTimestamp
	private Instant atualizadoEm;

	protected ConfiguracaoDaCobranca() {
	}

	/** Padrão: rateio pela fração ideal, vencimento dia 10, multa 2%, juros 1% ao mês, sem fundo de reserva. */
	public static ConfiguracaoDaCobranca padrao(UUID tenantId) {
		var c = new ConfiguracaoDaCobranca();
		c.tenantId = Objects.requireNonNull(tenantId);
		c.alterar(TipoDeRateio.FRACAO_IDEAL, 10, MULTA_MAXIMA, BigDecimal.ONE, BigDecimal.ZERO);
		return c;
	}

	public void alterar(TipoDeRateio tipoRateio, int diaVencimento, BigDecimal multa, BigDecimal jurosMensal,
			BigDecimal fundoReserva) {
		if (diaVencimento < 1 || diaVencimento > 28) {
			throw new IllegalArgumentException("O dia de vencimento deve ficar entre 1 e 28 (vale para todos os meses)");
		}
		if (multa.signum() < 0 || multa.compareTo(MULTA_MAXIMA) > 0) {
			throw new IllegalArgumentException("A multa por atraso pode ser de no máximo 2% (Código Civil, art. 1.336)");
		}
		if (jurosMensal.signum() < 0 || jurosMensal.compareTo(BigDecimal.TEN) > 0) {
			throw new IllegalArgumentException("Os juros devem ficar entre 0% e 10% ao mês");
		}
		if (fundoReserva.signum() < 0 || fundoReserva.compareTo(Dinheiro.CEM) > 0) {
			throw new IllegalArgumentException("O fundo de reserva deve ficar entre 0% e 100% da taxa ordinária");
		}
		this.tipoRateio = Objects.requireNonNull(tipoRateio);
		this.diaVencimento = (short) diaVencimento;
		this.multaPercentual = multa;
		this.jurosMensalPercentual = jurosMensal;
		this.fundoReservaPercentual = fundoReserva;
	}

	public UUID getId() { return id; }
	public TipoDeRateio getTipoRateio() { return tipoRateio; }
	public int getDiaVencimento() { return diaVencimento; }
	public BigDecimal getMultaPercentual() { return multaPercentual; }
	public BigDecimal getJurosMensalPercentual() { return jurosMensalPercentual; }
	public BigDecimal getFundoReservaPercentual() { return fundoReservaPercentual; }

}
