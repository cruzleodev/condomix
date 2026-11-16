package br.com.condomix.financeiro.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

import java.util.Objects;
import java.util.UUID;

/** Uma unidade dentro de um grupo. Cada unidade fica em no máximo um grupo. */
@Entity
@Table(name = "grupo_de_unidades_membro")
public class MembroDeGrupo {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@TenantId
	private UUID tenantId;

	private UUID unidadeId;

	private UUID grupoId;

	protected MembroDeGrupo() {
	}

	public MembroDeGrupo(UUID tenantId, UUID unidadeId, UUID grupoId) {
		this.tenantId = Objects.requireNonNull(tenantId);
		this.unidadeId = Objects.requireNonNull(unidadeId);
		this.grupoId = Objects.requireNonNull(grupoId);
	}

	public UUID getUnidadeId() { return unidadeId; }
	public UUID getGrupoId() { return grupoId; }

}
