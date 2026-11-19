package br.com.condomix.financeiro.dominio;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * O que uma unidade deve pagar ao condomínio. O valor é a soma dos itens. Multa e juros não são
 * gravados: são calculados na data ({@link #encargosEm}), com as regras do momento da emissão.
 */
@Entity
@Table(name = "cobranca")
public class Cobranca {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@TenantId
	private UUID tenantId;

	private UUID unidadeId;

	private UUID responsavelPessoaId;

	@Enumerated(EnumType.STRING)
	private OrigemCobranca origem;

	private UUID geracaoId;

	private LocalDate competencia;

	private String descricao;

	private LocalDate vencimento;

	private BigDecimal valor;

	private BigDecimal multaPercentual;

	private BigDecimal jurosMensalPercentual;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "cobranca_item", joinColumns = @JoinColumn(name = "cobranca_id"))
	@OrderColumn(name = "ordem")
	private List<ItemDeCobranca> itens = new ArrayList<>();

	@Enumerated(EnumType.STRING)
	private StatusCobranca status;

	private LocalDate pagoEm;

	private BigDecimal valorPago;

	@Enumerated(EnumType.STRING)
	private FormaDePagamento formaPagamento;

	private String observacaoPagamento;

	private UUID baixaRegistradaPor;

	private Instant canceladaEm;

	private String motivoCancelamento;

	private UUID criadaPor;

	@CreationTimestamp
	private Instant criadoEm;

	@UpdateTimestamp
	private Instant atualizadoEm;

	protected Cobranca() {
	}

	/** Dados comuns a toda cobrança nova. */
	public record Emissao(UUID tenantId, UUID unidadeId, UUID responsavelPessoaId, OrigemCobranca origem,
			UUID geracaoId, YearMonth competencia, String descricao, LocalDate vencimento, List<ItemDeCobranca> itens,
			ConfiguracaoDaCobranca regras, UUID criadaPor) {
	}

	public Cobranca(Emissao e) {
		BigDecimal soma = e.itens().stream().map(ItemDeCobranca::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
		if (soma.signum() <= 0) {
			throw new IllegalArgumentException("O valor da cobrança deve ser maior que zero");
		}
		if (e.descricao() == null || e.descricao().isBlank()) {
			throw new IllegalArgumentException("Informe a descrição da cobrança");
		}
		this.tenantId = Objects.requireNonNull(e.tenantId());
		this.unidadeId = Objects.requireNonNull(e.unidadeId());
		this.responsavelPessoaId = e.responsavelPessoaId();
		this.origem = Objects.requireNonNull(e.origem());
		this.geracaoId = e.geracaoId();
		this.competencia = e.competencia() == null ? null : e.competencia().atDay(1);
		this.descricao = e.descricao().strip();
		this.vencimento = Objects.requireNonNull(e.vencimento(), "Informe o vencimento");
		this.itens = new ArrayList<>(e.itens());
		this.valor = Dinheiro.reais(soma);
		this.multaPercentual = e.regras().getMultaPercentual();
		this.jurosMensalPercentual = e.regras().getJurosMensalPercentual();
		this.status = StatusCobranca.ABERTA;
		this.criadaPor = e.criadaPor();
	}

	public boolean vencidaEm(LocalDate data) {
		return status == StatusCobranca.ABERTA && data.isAfter(vencimento);
	}

	/**
	 * Valor na data: multa (uma vez) e juros simples pro rata dia (juros ao mês ÷ 30 × dias de atraso).
	 * Para cobranças pagas, considera a data do pagamento.
	 */
	public Encargos encargosEm(LocalDate data) {
		LocalDate referencia = status == StatusCobranca.PAGA ? pagoEm : data;
		long dias = Math.max(0, ChronoUnit.DAYS.between(vencimento, referencia));
		if (dias == 0) {
			return new Encargos(0, valor, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), valor);
		}
		BigDecimal multa = Dinheiro.percentual(valor, multaPercentual);
		BigDecimal juros = Dinheiro.reais(valor.multiply(jurosMensalPercentual)
				.multiply(BigDecimal.valueOf(dias))
				.divide(BigDecimal.valueOf(3000), 10, RoundingMode.HALF_UP));
		return new Encargos(dias, valor, multa, juros, valor.add(multa).add(juros));
	}

	/**
	 * Valor, vencimento e encargos para emitir o boleto hoje. Ainda no prazo: o valor e o vencimento da cobrança,
	 * com multa e juros configurados no boleto. Já vencida (o gateway não aceita vencimento no passado): o valor
	 * atualizado até hoje, vencendo hoje, só com juros dali em diante (a multa já está no valor).
	 */
	public CondicoesDoBoleto condicoesDoBoletoEm(LocalDate hoje) {
		if (!hoje.isAfter(vencimento)) {
			return new CondicoesDoBoleto(valor, vencimento, multaPercentual, jurosMensalPercentual);
		}
		return new CondicoesDoBoleto(encargosEm(hoje).total(), hoje, BigDecimal.ZERO, jurosMensalPercentual);
	}

	public record CondicoesDoBoleto(BigDecimal valor, LocalDate vencimento, BigDecimal multaPercentual,
			BigDecimal jurosMensalPercentual) {
	}

	/** Baixa manual. Sem {@code valorPago}, usa o valor com encargos na data do pagamento. */
	public void registrarPagamento(LocalDate data, BigDecimal valorPago, FormaDePagamento forma, String observacao,
			UUID registradoPor) {
		if (status != StatusCobranca.ABERTA) {
			throw new IllegalStateException("Só cobranças em aberto podem ser pagas (esta está " + status + ")");
		}
		Objects.requireNonNull(data, "Informe a data do pagamento");
		BigDecimal valorFinal = valorPago != null ? valorPago : encargosEm(data).total();
		if (valorFinal.signum() < 0) {
			throw new IllegalArgumentException("O valor pago não pode ser negativo");
		}
		this.pagoEm = data;
		this.valorPago = Dinheiro.reais(valorFinal);
		this.formaPagamento = Objects.requireNonNull(forma, "Informe a forma de pagamento");
		this.observacaoPagamento = observacao;
		this.baixaRegistradaPor = registradoPor;
		this.status = StatusCobranca.PAGA;
	}

	/** Desfaz uma baixa registrada por engano. */
	public void estornarPagamento() {
		if (status != StatusCobranca.PAGA) {
			throw new IllegalStateException("Só cobranças pagas podem ter o pagamento estornado");
		}
		this.pagoEm = null;
		this.valorPago = null;
		this.formaPagamento = null;
		this.observacaoPagamento = null;
		this.baixaRegistradaPor = null;
		this.status = StatusCobranca.ABERTA;
	}

	public void cancelar(String motivo, Instant quando) {
		if (status != StatusCobranca.ABERTA) {
			throw new IllegalStateException("Só cobranças em aberto podem ser canceladas");
		}
		this.motivoCancelamento = motivo;
		this.canceladaEm = Objects.requireNonNull(quando);
		this.status = StatusCobranca.CANCELADA;
	}

	public UUID getId() { return id; }
	public UUID getTenantId() { return tenantId; }
	public UUID getUnidadeId() { return unidadeId; }
	public UUID getResponsavelPessoaId() { return responsavelPessoaId; }
	public OrigemCobranca getOrigem() { return origem; }
	public UUID getGeracaoId() { return geracaoId; }
	public YearMonth getCompetencia() { return competencia == null ? null : YearMonth.from(competencia); }
	public String getDescricao() { return descricao; }
	public LocalDate getVencimento() { return vencimento; }
	public BigDecimal getValor() { return valor; }
	public List<ItemDeCobranca> getItens() { return List.copyOf(itens); }
	public StatusCobranca getStatus() { return status; }
	public LocalDate getPagoEm() { return pagoEm; }
	public BigDecimal getValorPago() { return valorPago; }
	public FormaDePagamento getFormaPagamento() { return formaPagamento; }
	public String getMotivoCancelamento() { return motivoCancelamento; }
	public Instant getCriadoEm() { return criadoEm; }

}
