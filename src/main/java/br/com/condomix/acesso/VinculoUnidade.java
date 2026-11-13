package br.com.condomix.acesso;

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
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Liga uma pessoa a uma unidade (proprietário, inquilino, morador ou dependente). */
@Entity
@Table(name = "vinculo_unidade")
public class VinculoUnidade {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@TenantId
	private UUID tenantId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pessoa_id")
	private Pessoa pessoa;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "unidade_id")
	private Unidade unidade;

	@Enumerated(EnumType.STRING)
	private TipoVinculo tipo;

	private LocalDate inicio;

	private LocalDate fim;

	@CreationTimestamp
	private Instant criadoEm;

	protected VinculoUnidade() {
	}

	public VinculoUnidade(Pessoa pessoa, Unidade unidade, TipoVinculo tipo, LocalDate inicio) {
		if (!pessoa.getTenantId().equals(unidade.getTenantId())) {
			throw new IllegalArgumentException("Pessoa e unidade são de condomínios diferentes");
		}
		this.tenantId = unidade.getTenantId();
		this.pessoa = pessoa;
		this.unidade = unidade;
		this.tipo = Objects.requireNonNull(tipo);
		this.inicio = Objects.requireNonNull(inicio);
	}

	public void encerrar(LocalDate fim) {
		this.fim = fim;
	}

	public boolean isVigente(LocalDate data) {
		return !data.isBefore(inicio) && (fim == null || !data.isAfter(fim));
	}

	public UUID getId() { return id; }
	public UUID getTenantId() { return tenantId; }
	public Pessoa getPessoa() { return pessoa; }
	public Unidade getUnidade() { return unidade; }
	public TipoVinculo getTipo() { return tipo; }
	public LocalDate getInicio() { return inicio; }
	public LocalDate getFim() { return fim; }
	public Instant getCriadoEm() { return criadoEm; }

}
