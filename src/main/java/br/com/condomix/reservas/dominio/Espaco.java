package br.com.condomix.reservas.dominio;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Um espaço comum que os moradores reservam: os horários, o aluguel e as regras. */
@Entity
@Table(name = "espaco")
public class Espaco {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@TenantId
	private UUID tenantId;

	private String nome;

	private String descricao;

	private Integer capacidade;

	private BigDecimal valorAluguel;

	@Embedded
	private RegrasDoEspaco regras;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "periodo_do_espaco", joinColumns = @JoinColumn(name = "espaco_id"))
	@OrderBy("inicio")
	private List<PeriodoDoEspaco> periodos = new ArrayList<>();

	private boolean ativo = true;

	@CreationTimestamp
	private Instant criadoEm;

	@UpdateTimestamp
	private Instant atualizadoEm;

	protected Espaco() {
	}

	public Espaco(UUID tenantId, DadosDoEspaco dados) {
		this.tenantId = Objects.requireNonNull(tenantId);
		alterar(dados);
	}

	/** Tudo o que o síndico configura num espaço. */
	public record DadosDoEspaco(String nome, String descricao, Integer capacidade, BigDecimal valorAluguel,
			RegrasDoEspaco regras, List<PeriodoDoEspaco> periodos) {
	}

	/**
	 * Muda o cadastro. As reservas já feitas guardam o horário e o valor de quando foram feitas.
	 * Os horários não podem se sobrepor: assim, cada horário de um dia tem no máximo uma reserva.
	 */
	public void alterar(DadosDoEspaco dados) {
		if (dados.nome() == null || dados.nome().isBlank()) {
			throw new IllegalArgumentException("Dê um nome ao espaço");
		}
		if (dados.capacidade() != null && dados.capacidade() <= 0) {
			throw new IllegalArgumentException("A capacidade deve ser maior que zero (ou vazia)");
		}
		BigDecimal valor = dados.valorAluguel() == null ? BigDecimal.ZERO : dados.valorAluguel();
		if (valor.signum() < 0) {
			throw new IllegalArgumentException("O aluguel não pode ser negativo");
		}
		List<PeriodoDoEspaco> novos = dados.periodos() == null ? List.of() : dados.periodos();
		if (novos.isEmpty()) {
			throw new IllegalArgumentException("Cadastre pelo menos um horário (ex.: Dia inteiro, das 8h às 22h)");
		}
		for (int i = 0; i < novos.size(); i++) {
			for (int j = i + 1; j < novos.size(); j++) {
				PeriodoDoEspaco a = novos.get(i);
				PeriodoDoEspaco b = novos.get(j);
				if (a.nome().equalsIgnoreCase(b.nome())) {
					throw new IllegalArgumentException("Dois horários com o mesmo nome: " + a.nome());
				}
				if (a.sobrepoe(b.inicio(), b.fim())) {
					throw new IllegalArgumentException("Os horários \"" + a.nome() + "\" e \"" + b.nome() + "\" se sobrepõem");
				}
			}
		}
		this.nome = dados.nome().strip();
		this.descricao = dados.descricao() == null || dados.descricao().isBlank() ? null : dados.descricao().strip();
		this.capacidade = dados.capacidade();
		this.valorAluguel = valor.setScale(2, java.math.RoundingMode.HALF_UP);
		this.regras = dados.regras() == null ? RegrasDoEspaco.padrao() : dados.regras();
		this.periodos.clear();
		this.periodos.addAll(novos);
	}

	/** Não aceita reservas novas. As já feitas continuam valendo. */
	public void desativar() {
		this.ativo = false;
	}

	public void reativar() {
		this.ativo = true;
	}

	public Optional<PeriodoDoEspaco> periodo(String nome) {
		return periodos.stream().filter(p -> p.nome().equalsIgnoreCase(nome == null ? "" : nome.strip())).findFirst();
	}

	public boolean cobraAluguel() {
		return valorAluguel.signum() > 0;
	}

	public UUID getId() { return id; }
	public UUID getTenantId() { return tenantId; }
	public String getNome() { return nome; }
	public String getDescricao() { return descricao; }
	public Integer getCapacidade() { return capacidade; }
	public BigDecimal getValorAluguel() { return valorAluguel; }
	public RegrasDoEspaco getRegras() { return regras; }
	public List<PeriodoDoEspaco> getPeriodos() { return List.copyOf(periodos); }
	public boolean isAtivo() { return ativo; }

}
