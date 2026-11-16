package br.com.condomix.plataforma;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** Plano vendido pelo Condomix. Os planos iniciais são criados pela migração V1. */
@Entity
@Table(name = "plano")
public class Plano {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	private String codigo;

	private String nome;

	private String descricao;

	@Enumerated(EnumType.STRING)
	private TipoPlano tipo;

	private boolean ativo = true;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "plano_modulo", joinColumns = @JoinColumn(name = "plano_id"))
	@Column(name = "modulo")
	@Enumerated(EnumType.STRING)
	private Set<Modulo> modulos = EnumSet.noneOf(Modulo.class);

	/** Preços definidos pelo administrador; vazios enquanto não forem definidos. */
	@Column(precision = 10, scale = 2)
	private BigDecimal precoMensal;

	@Column(precision = 10, scale = 2)
	private BigDecimal precoSemestral;

	@Column(precision = 10, scale = 2)
	private BigDecimal precoAnual;

	@CreationTimestamp
	private Instant criadoEm;

	protected Plano() {
	}

	public boolean libera(Modulo modulo) {
		return ativo && modulos.contains(modulo);
	}

	/** Define os preços de cada periodicidade. Valores em reais, sem negativos. */
	public void definirPrecos(BigDecimal mensal, BigDecimal semestral, BigDecimal anual) {
		for (BigDecimal preco : new BigDecimal[] { mensal, semestral, anual }) {
			if (preco != null && preco.signum() < 0) {
				throw new IllegalArgumentException("Preço não pode ser negativo");
			}
		}
		this.precoMensal = mensal;
		this.precoSemestral = semestral;
		this.precoAnual = anual;
	}

	public UUID getId() { return id; }
	public String getCodigo() { return codigo; }
	public String getNome() { return nome; }
	public String getDescricao() { return descricao; }
	public TipoPlano getTipo() { return tipo; }
	public boolean isAtivo() { return ativo; }
	public Set<Modulo> getModulos() { return Set.copyOf(modulos); }
	public Instant getCriadoEm() { return criadoEm; }
	public BigDecimal getPrecoMensal() { return precoMensal; }
	public BigDecimal getPrecoSemestral() { return precoSemestral; }
	public BigDecimal getPrecoAnual() { return precoAnual; }

}
