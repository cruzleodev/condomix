package br.com.condomix.documentos.dominio;

import br.com.condomix.documentos.Categoria;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Um documento do condomínio. Nunca é apagado de verdade: {@link #remover} só marca, e o Hibernate
 * passa a ignorá-lo em todas as consultas ({@code @SQLRestriction}).
 */
@Entity
@Table(name = "documento")
@SQLRestriction("removido_em is null")
public class Documento {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@TenantId
	private UUID tenantId;

	private String titulo;

	private String descricao;

	@Enumerated(EnumType.STRING)
	private Categoria categoria;

	private LocalDate dataReferencia;

	@Embedded
	private ArquivoDoDocumento arquivo;

	private UUID enviadoPor;

	@CreationTimestamp
	private Instant enviadoEm;

	@UpdateTimestamp
	private Instant atualizadoEm;

	private Instant removidoEm;

	private UUID removidoPor;

	protected Documento() {
	}

	public Documento(UUID tenantId, DadosDoDocumento dados, ArquivoDoDocumento arquivo, UUID enviadoPor) {
		this.tenantId = Objects.requireNonNull(tenantId);
		this.arquivo = Objects.requireNonNull(arquivo);
		this.enviadoPor = Objects.requireNonNull(enviadoPor);
		aplicar(dados);
	}

	public void alterar(DadosDoDocumento dados) {
		aplicar(dados);
	}

	public void remover(UUID usuario, Instant quando) {
		this.removidoPor = Objects.requireNonNull(usuario);
		this.removidoEm = Objects.requireNonNull(quando);
	}

	private void aplicar(DadosDoDocumento dados) {
		this.titulo = dados.titulo();
		this.categoria = dados.categoria();
		this.descricao = dados.descricao();
		this.dataReferencia = dados.dataReferencia();
	}

	public UUID getId() { return id; }
	public UUID getTenantId() { return tenantId; }
	public String getTitulo() { return titulo; }
	public String getDescricao() { return descricao; }
	public Categoria getCategoria() { return categoria; }
	public LocalDate getDataReferencia() { return dataReferencia; }
	public ArquivoDoDocumento getArquivo() { return arquivo; }
	public UUID getEnviadoPor() { return enviadoPor; }
	public Instant getEnviadoEm() { return enviadoEm; }
	public Instant getAtualizadoEm() { return atualizadoEm; }
	public boolean isRemovido() { return removidoEm != null; }

}
