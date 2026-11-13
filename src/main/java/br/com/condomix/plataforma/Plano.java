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

	@CreationTimestamp
	private Instant criadoEm;

	protected Plano() {
	}

	public boolean libera(Modulo modulo) {
		return ativo && modulos.contains(modulo);
	}

	public UUID getId() { return id; }
	public String getCodigo() { return codigo; }
	public String getNome() { return nome; }
	public String getDescricao() { return descricao; }
	public TipoPlano getTipo() { return tipo; }
	public boolean isAtivo() { return ativo; }
	public Set<Modulo> getModulos() { return Set.copyOf(modulos); }
	public Instant getCriadoEm() { return criadoEm; }

}
