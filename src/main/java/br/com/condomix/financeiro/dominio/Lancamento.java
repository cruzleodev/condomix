package br.com.condomix.financeiro.dominio;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Uma entrada ou saída de dinheiro de uma conta. Nunca é apagado: um erro se corrige cancelando. */
@Entity
@Table(name = "lancamento")
public class Lancamento {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@TenantId
	private UUID tenantId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "conta_id")
	private ContaFinanceira conta;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "categoria_id")
	private CategoriaFinanceira categoria;

	@Enumerated(EnumType.STRING)
	private TipoDeLancamento tipo;

	@Enumerated(EnumType.STRING)
	private OrigemDoLancamento origem;

	private LocalDate data;

	private String descricao;

	private BigDecimal valor;

	private String favorecido;

	private UUID cobrancaId;

	private UUID transferenciaId;

	private Instant canceladoEm;

	private String motivoCancelamento;

	private UUID criadoPor;

	@CreationTimestamp
	private Instant criadoEm;

	protected Lancamento() {
	}

	/** Receita ou despesa. A categoria tem que ser da mesma natureza. */
	public static Lancamento movimento(UUID tenantId, ContaFinanceira conta, CategoriaFinanceira categoria,
			TipoDeLancamento tipo, OrigemDoLancamento origem, LocalDate data, String descricao, BigDecimal valor,
			String favorecido, UUID cobrancaId, UUID criadoPor) {
		if (tipo.natureza() == null) {
			throw new IllegalArgumentException("Use uma transferência para mover dinheiro entre contas");
		}
		if (categoria.getNatureza() != tipo.natureza()) {
			throw new IllegalArgumentException("A categoria " + categoria.getNome() + " é de " + categoria.getNatureza()
					+ ", não de " + tipo.natureza());
		}
		var l = new Lancamento(tenantId, conta, tipo, origem, data, descricao, valor, criadoPor);
		l.categoria = categoria;
		l.favorecido = favorecido;
		l.cobrancaId = cobrancaId;
		return l;
	}

	public static Lancamento transferencia(UUID tenantId, ContaFinanceira conta, TipoDeLancamento tipo, LocalDate data,
			String descricao, BigDecimal valor, UUID transferenciaId, UUID criadoPor) {
		if (tipo.natureza() != null) {
			throw new IllegalArgumentException("Tipo de transferência inválido");
		}
		var l = new Lancamento(tenantId, conta, tipo, OrigemDoLancamento.TRANSFERENCIA, data, descricao, valor, criadoPor);
		l.transferenciaId = Objects.requireNonNull(transferenciaId);
		return l;
	}

	private Lancamento(UUID tenantId, ContaFinanceira conta, TipoDeLancamento tipo, OrigemDoLancamento origem,
			LocalDate data, String descricao, BigDecimal valor, UUID criadoPor) {
		if (valor == null || valor.signum() <= 0) {
			throw new IllegalArgumentException("O valor deve ser maior que zero");
		}
		if (descricao == null || descricao.isBlank()) {
			throw new IllegalArgumentException("Informe a descrição");
		}
		Objects.requireNonNull(data, "Informe a data");
		conta.conferirData(data);
		this.tenantId = Objects.requireNonNull(tenantId);
		this.conta = conta;
		this.tipo = tipo;
		this.origem = origem;
		this.data = data;
		this.descricao = descricao.strip();
		this.valor = Dinheiro.reais(valor);
		this.criadoPor = criadoPor;
	}

	public void cancelar(String motivo, Instant quando) {
		if (canceladoEm != null) {
			throw new IllegalStateException("Este lançamento já foi cancelado");
		}
		this.canceladoEm = Objects.requireNonNull(quando);
		this.motivoCancelamento = motivo;
	}

	public boolean isCancelado() {
		return canceladoEm != null;
	}

	/** Valor com sinal: positivo entra, negativo sai. */
	public BigDecimal valorComSinal() {
		return tipo.sinal() > 0 ? valor : valor.negate();
	}

	public UUID getId() { return id; }
	public ContaFinanceira getConta() { return conta; }
	public CategoriaFinanceira getCategoria() { return categoria; }
	public TipoDeLancamento getTipo() { return tipo; }
	public OrigemDoLancamento getOrigem() { return origem; }
	public LocalDate getData() { return data; }
	public String getDescricao() { return descricao; }
	public BigDecimal getValor() { return valor; }
	public String getFavorecido() { return favorecido; }
	public UUID getCobrancaId() { return cobrancaId; }
	public UUID getTransferenciaId() { return transferenciaId; }
	public String getMotivoCancelamento() { return motivoCancelamento; }

}
