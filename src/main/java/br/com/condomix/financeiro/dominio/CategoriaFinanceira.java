package br.com.condomix.financeiro.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

import java.util.Objects;
import java.util.UUID;

/** Uma linha do plano de contas. As do sistema têm {@code codigo} (de {@link CategoriaPadrao}). */
@Entity
@Table(name = "categoria_financeira")
public class CategoriaFinanceira {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@TenantId
	private UUID tenantId;

	@Enumerated(EnumType.STRING)
	private CategoriaPadrao codigo;

	private String nome;

	@Enumerated(EnumType.STRING)
	private Natureza natureza;

	private boolean ativa = true;

	protected CategoriaFinanceira() {
	}

	public static CategoriaFinanceira padrao(UUID tenantId, CategoriaPadrao padrao) {
		var c = new CategoriaFinanceira(tenantId, padrao.nome(), padrao.natureza());
		c.codigo = padrao;
		return c;
	}

	public CategoriaFinanceira(UUID tenantId, String nome, Natureza natureza) {
		if (nome == null || nome.isBlank()) {
			throw new IllegalArgumentException("Informe o nome da categoria");
		}
		this.tenantId = Objects.requireNonNull(tenantId);
		this.nome = nome.strip();
		this.natureza = Objects.requireNonNull(natureza, "Informe se é RECEITA ou DESPESA");
	}

	public void renomear(String nome) {
		if (nome == null || nome.isBlank()) {
			throw new IllegalArgumentException("Informe o nome da categoria");
		}
		this.nome = nome.strip();
	}

	public void desativar() {
		this.ativa = false;
	}

	public UUID getId() { return id; }
	public CategoriaPadrao getCodigo() { return codigo; }
	public String getNome() { return nome; }
	public Natureza getNatureza() { return natureza; }
	public boolean isAtiva() { return ativa; }

}
