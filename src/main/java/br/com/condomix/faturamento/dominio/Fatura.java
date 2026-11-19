package br.com.condomix.faturamento.dominio;

import br.com.condomix.faturamento.SituacaoDaFatura;
import br.com.condomix.pagamentos.GatewayDePagamento.BoletoEmitido;
import br.com.condomix.pagamentos.GatewayDePagamento.FormaDoPagamento;
import br.com.condomix.pagamentos.GatewayDePagamento.PagamentoConfirmado;
import br.com.condomix.pagamentos.TipoDeGateway;
import br.com.condomix.plataforma.Periodicidade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Fatura da assinatura do Condomix: um período do plano, cobrado do condomínio por boleto e Pix na conta do
 * Condomix. Tem {@code tenant_id} para o síndico ver só as faturas do próprio condomínio.
 */
@Entity
@Table(name = "fatura")
public class Fatura {

	private static final int TAMANHO_DO_ERRO = 500;

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@TenantId
	private UUID tenantId;

	private UUID planoId;

	private String nomeDoPlano;

	@Enumerated(EnumType.STRING)
	private Periodicidade periodicidade;

	private LocalDate periodoInicio;

	private LocalDate periodoFim;

	private BigDecimal valor;

	private LocalDate vencimento;

	@Enumerated(EnumType.STRING)
	private SituacaoDaFatura situacao;

	@Enumerated(EnumType.STRING)
	private TipoDeGateway gateway;

	private String idNoGateway;

	private String url;

	private String linhaDigitavel;

	@Column(name = "pix_copia_e_cola")
	private String pixCopiaECola;

	private String pixQrCode;

	private String erro;

	private int tentativas;

	private LocalDate pagoEm;

	private BigDecimal valorPago;

	@Enumerated(EnumType.STRING)
	@Column(name = "forma_pagamento")
	private FormaDoPagamento forma;

	private BigDecimal tarifa;

	private Instant canceladaEm;

	private UUID canceladaPor;

	@CreationTimestamp
	private Instant criadoEm;

	@UpdateTimestamp
	private Instant atualizadoEm;

	protected Fatura() {
	}

	public Fatura(UUID condominioId, UUID planoId, String nomeDoPlano, Periodicidade periodicidade,
			CalendarioDeFaturas.Periodo periodo, BigDecimal valor) {
		if (valor == null || valor.signum() <= 0) {
			throw new IllegalArgumentException("A fatura precisa de um valor positivo");
		}
		this.tenantId = Objects.requireNonNull(condominioId);
		this.planoId = Objects.requireNonNull(planoId);
		this.nomeDoPlano = Objects.requireNonNull(nomeDoPlano);
		this.periodicidade = Objects.requireNonNull(periodicidade);
		this.periodoInicio = periodo.inicio();
		this.periodoFim = periodo.fim();
		this.vencimento = periodo.vencimento();
		this.valor = valor;
		this.situacao = SituacaoDaFatura.A_EMITIR;
	}

	/**
	 * O boleto saiu no gateway. Se a emissão atrasou (ex.: depois de uma falha corrigida), o vencimento passa
	 * para {@code vencimentoDoBoleto}: o condomínio não tinha como pagar antes.
	 */
	public void emitida(TipoDeGateway gateway, BoletoEmitido emitido, LocalDate vencimentoDoBoleto) {
		exigir(SituacaoDaFatura.A_EMITIR);
		this.gateway = Objects.requireNonNull(gateway);
		this.idNoGateway = Objects.requireNonNull(emitido.idNoGateway());
		this.url = emitido.url();
		this.linhaDigitavel = emitido.linhaDigitavel();
		this.pixCopiaECola = emitido.pixCopiaECola();
		this.pixQrCode = emitido.pixQrCode();
		this.vencimento = Objects.requireNonNull(vencimentoDoBoleto);
		this.erro = null;
		this.tentativas++;
		this.situacao = SituacaoDaFatura.EMITIDA;
	}

	public void falhou(String motivo) {
		exigir(SituacaoDaFatura.A_EMITIR);
		this.erro = motivo == null ? "O gateway não respondeu" : motivo.substring(0, Math.min(motivo.length(), TAMANHO_DO_ERRO));
		this.tentativas++;
		this.situacao = SituacaoDaFatura.FALHOU;
	}

	/** O administrador corrigiu o motivo (ex.: cadastrou o CNPJ) e pede outra tentativa. */
	public void tentarDeNovo() {
		exigir(SituacaoDaFatura.FALHOU);
		this.situacao = SituacaoDaFatura.A_EMITIR;
	}

	/**
	 * O administrador perdoou a fatura. Se o boleto já existe no gateway, ele é cancelado lá em seguida.
	 * O período continua contado: a próxima fatura começa depois dele.
	 */
	public void cancelar(Instant quando, UUID por) {
		if (situacao == SituacaoDaFatura.PAGA) {
			throw new IllegalStateException("Fatura paga não pode ser cancelada");
		}
		if (situacao == SituacaoDaFatura.CANCELADA || situacao == SituacaoDaFatura.A_CANCELAR) {
			return;
		}
		this.canceladaEm = Objects.requireNonNull(quando);
		this.canceladaPor = por;
		this.situacao = situacao == SituacaoDaFatura.EMITIDA ? SituacaoDaFatura.A_CANCELAR : SituacaoDaFatura.CANCELADA;
	}

	public void canceladaNoGateway() {
		exigir(SituacaoDaFatura.A_CANCELAR);
		this.situacao = SituacaoDaFatura.CANCELADA;
	}

	/** Paga no gateway. Uma cancelada que ainda não saiu do gateway também pode ser paga: o dinheiro entrou. */
	public void paga(PagamentoConfirmado pagamento) {
		if (situacao != SituacaoDaFatura.EMITIDA && situacao != SituacaoDaFatura.A_CANCELAR) {
			throw new IllegalStateException("A fatura não está esperando pagamento (está " + situacao + ")");
		}
		this.pagoEm = Objects.requireNonNull(pagamento.data());
		this.valorPago = pagamento.valorPago();
		this.tarifa = pagamento.tarifa();
		this.forma = pagamento.forma();
		this.situacao = SituacaoDaFatura.PAGA;
	}

	/** Emitida e com o vencimento no passado. */
	public boolean atrasadaEm(LocalDate hoje) {
		return situacao == SituacaoDaFatura.EMITIDA && vencimento.isBefore(hoje);
	}

	/** Quando o acesso é suspenso, se a fatura continuar sem pagamento; vazio se ela não está esperando pagamento. */
	public LocalDate suspensaoEm() {
		return situacao == SituacaoDaFatura.EMITIDA ? CalendarioDeFaturas.suspensaoEm(vencimento) : null;
	}

	private void exigir(SituacaoDaFatura esperada) {
		if (situacao != esperada) {
			throw new IllegalStateException("A fatura está " + situacao + ", não " + esperada);
		}
	}

	public UUID getId() { return id; }
	public UUID getTenantId() { return tenantId; }
	public UUID getPlanoId() { return planoId; }
	public String getNomeDoPlano() { return nomeDoPlano; }
	public Periodicidade getPeriodicidade() { return periodicidade; }
	public LocalDate getPeriodoInicio() { return periodoInicio; }
	public LocalDate getPeriodoFim() { return periodoFim; }
	public BigDecimal getValor() { return valor; }
	public LocalDate getVencimento() { return vencimento; }
	public SituacaoDaFatura getSituacao() { return situacao; }
	public TipoDeGateway getGateway() { return gateway; }
	public String getIdNoGateway() { return idNoGateway; }
	public String getUrl() { return url; }
	public String getLinhaDigitavel() { return linhaDigitavel; }
	public String getPixCopiaECola() { return pixCopiaECola; }
	public String getPixQrCode() { return pixQrCode; }
	public String getErro() { return erro; }
	public int getTentativas() { return tentativas; }
	public LocalDate getPagoEm() { return pagoEm; }
	public BigDecimal getValorPago() { return valorPago; }
	public FormaDoPagamento getForma() { return forma; }
	public BigDecimal getTarifa() { return tarifa; }
	public Instant getCanceladaEm() { return canceladaEm; }
	public Instant getCriadoEm() { return criadoEm; }

}
