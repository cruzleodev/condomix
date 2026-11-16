package br.com.condomix.financeiro.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Grupo do rateio "valor por grupo": todas as unidades do grupo pagam o mesmo valor mensal. */
@Entity
@Table(name = "grupo_de_unidades")
public class GrupoDeUnidades {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@TenantId
	private UUID tenantId;

	private String nome;

	private BigDecimal valorMensal;

	protected GrupoDeUnidades() {
	}

	public GrupoDeUnidades(UUID tenantId, String nome, BigDecimal valorMensal) {
		this.tenantId = Objects.requireNonNull(tenantId);
		alterar(nome, valorMensal);
	}

	public void alterar(String nome, BigDecimal valorMensal) {
		if (nome == null || nome.isBlank()) {
			throw new IllegalArgumentException("Informe o nome do grupo (ex.: Apartamento tipo, Cobertura)");
		}
		if (valorMensal == null || valorMensal.signum() < 0) {
			throw new IllegalArgumentException("O valor mensal do grupo não pode ser negativo");
		}
		this.nome = nome.strip();
		this.valorMensal = Dinheiro.reais(valorMensal);
	}

	public UUID getId() { return id; }
	public String getNome() { return nome; }
	public BigDecimal getValorMensal() { return valorMensal; }

}
