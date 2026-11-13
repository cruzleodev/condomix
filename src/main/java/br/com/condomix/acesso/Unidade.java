package br.com.condomix.acesso;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Apartamento, casa, sala ou loja de um condomínio. */
@Entity
@Table(name = "unidade")
public class Unidade {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	private UUID tenantId;

	/** Torre, bloco ou quadra. Nulo quando o condomínio não tem essa divisão. */
	private String bloco;

	/** Ex.: "101", "Casa 12", "Loja 3". */
	private String identificacao;

	@Enumerated(EnumType.STRING)
	private TipoUnidade tipo;

	/** Usada no rateio das despesas do condomínio. */
	@Column(precision = 9, scale = 6)
	private BigDecimal fracaoIdeal;

	private boolean ativo = true;

	@CreationTimestamp
	private Instant criadoEm;

	@UpdateTimestamp
	private Instant atualizadoEm;

	protected Unidade() {
	}

	public Unidade(UUID tenantId, String bloco, String identificacao, TipoUnidade tipo) {
		this.tenantId = Objects.requireNonNull(tenantId);
		this.bloco = bloco;
		this.identificacao = Objects.requireNonNull(identificacao);
		this.tipo = Objects.requireNonNull(tipo);
	}

	public void definirFracaoIdeal(BigDecimal fracaoIdeal) {
		this.fracaoIdeal = fracaoIdeal;
	}

	public UUID getId() { return id; }
	public UUID getTenantId() { return tenantId; }
	public String getBloco() { return bloco; }
	public String getIdentificacao() { return identificacao; }
	public TipoUnidade getTipo() { return tipo; }
	public BigDecimal getFracaoIdeal() { return fracaoIdeal; }
	public boolean isAtivo() { return ativo; }
	public Instant getCriadoEm() { return criadoEm; }
	public Instant getAtualizadoEm() { return atualizadoEm; }

}
