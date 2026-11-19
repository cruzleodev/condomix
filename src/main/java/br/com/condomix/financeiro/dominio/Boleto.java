package br.com.condomix.financeiro.dominio;

import br.com.condomix.financeiro.gateway.GatewayDePagamento.BoletoEmitido;
import br.com.condomix.financeiro.TipoDeGateway;
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

/** O boleto (com Pix) de uma cobrança no gateway: a 2ª via que o morador paga pelo app. */
@Entity
@Table(name = "boleto")
public class Boleto {

	private static final int TAMANHO_DO_ERRO = 500;

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@TenantId
	private UUID tenantId;

	private UUID cobrancaId;

	@Enumerated(EnumType.STRING)
	private TipoDeGateway gateway;

	@Enumerated(EnumType.STRING)
	private SituacaoDoBoleto situacao;

	private String idNoGateway;

	private BigDecimal valor;

	private LocalDate vencimento;

	private String url;

	private String linhaDigitavel;

	@Column(name = "pix_copia_e_cola")
	private String pixCopiaECola;

	private String pixQrCode;

	private String erro;

	private int tentativas;

	private BigDecimal tarifa;

	@CreationTimestamp
	private Instant criadoEm;

	@UpdateTimestamp
	private Instant atualizadoEm;

	protected Boleto() {
	}

	/** Pedido de emissão: o processador emite no gateway logo depois. */
	public Boleto(UUID tenantId, UUID cobrancaId, TipoDeGateway gateway) {
		this.tenantId = Objects.requireNonNull(tenantId);
		this.cobrancaId = Objects.requireNonNull(cobrancaId);
		this.gateway = Objects.requireNonNull(gateway);
		this.situacao = SituacaoDoBoleto.PENDENTE;
	}

	public void emitido(BoletoEmitido emitido, BigDecimal valor, LocalDate vencimento) {
		exigir(SituacaoDoBoleto.PENDENTE);
		this.idNoGateway = Objects.requireNonNull(emitido.idNoGateway());
		this.url = emitido.url();
		this.linhaDigitavel = emitido.linhaDigitavel();
		this.pixCopiaECola = emitido.pixCopiaECola();
		this.pixQrCode = emitido.pixQrCode();
		this.valor = valor;
		this.vencimento = vencimento;
		this.erro = null;
		this.tentativas++;
		this.situacao = SituacaoDoBoleto.EMITIDO;
	}

	public void falhou(String motivo) {
		exigir(SituacaoDoBoleto.PENDENTE);
		this.erro = motivo == null ? "O gateway não respondeu" : motivo.substring(0, Math.min(motivo.length(), TAMANHO_DO_ERRO));
		this.tentativas++;
		this.situacao = SituacaoDoBoleto.FALHOU;
	}

	/** O síndico corrigiu o motivo (ex.: cadastrou o CPF) e pede outra tentativa. */
	public void tentarDeNovo() {
		exigir(SituacaoDoBoleto.FALHOU);
		this.situacao = SituacaoDoBoleto.PENDENTE;
	}

	/**
	 * A cobrança foi cancelada ou paga por fora. Se o boleto já existe no gateway, o processador cancela lá;
	 * se ainda não foi emitido, só deixa de ser emitido.
	 */
	public void pedirCancelamento() {
		if (situacao == SituacaoDoBoleto.PENDENTE || situacao == SituacaoDoBoleto.FALHOU) {
			this.situacao = SituacaoDoBoleto.CANCELADO;
		}
		else if (situacao == SituacaoDoBoleto.EMITIDO) {
			this.situacao = SituacaoDoBoleto.A_CANCELAR;
		}
		// pago, já cancelado ou a cancelar: nada a fazer
	}

	public void cancelado() {
		exigir(SituacaoDoBoleto.A_CANCELAR);
		this.situacao = SituacaoDoBoleto.CANCELADO;
	}

	public void pago(BigDecimal tarifa) {
		if (situacao != SituacaoDoBoleto.EMITIDO && situacao != SituacaoDoBoleto.A_CANCELAR) {
			throw new IllegalStateException("O boleto não está esperando pagamento (está " + situacao + ")");
		}
		this.tarifa = tarifa;
		this.situacao = SituacaoDoBoleto.PAGO;
	}

	private void exigir(SituacaoDoBoleto esperada) {
		if (situacao != esperada) {
			throw new IllegalStateException("O boleto está " + situacao + ", não " + esperada);
		}
	}

	public UUID getId() { return id; }
	public UUID getTenantId() { return tenantId; }
	public UUID getCobrancaId() { return cobrancaId; }
	public TipoDeGateway getGateway() { return gateway; }
	public SituacaoDoBoleto getSituacao() { return situacao; }
	public String getIdNoGateway() { return idNoGateway; }
	public BigDecimal getValor() { return valor; }
	public LocalDate getVencimento() { return vencimento; }
	public String getUrl() { return url; }
	public String getLinhaDigitavel() { return linhaDigitavel; }
	public String getPixCopiaECola() { return pixCopiaECola; }
	public String getPixQrCode() { return pixQrCode; }
	public String getErro() { return erro; }
	public int getTentativas() { return tentativas; }
	public BigDecimal getTarifa() { return tarifa; }

}
