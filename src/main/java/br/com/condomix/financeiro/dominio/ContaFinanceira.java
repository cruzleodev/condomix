package br.com.condomix.financeiro.dominio;

import br.com.condomix.compartilhado.FormatoBrasileiro;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Onde o dinheiro do condomínio fica. O saldo é o inicial mais os lançamentos a partir da data do saldo inicial. */
@Entity
@Table(name = "conta_financeira")
public class ContaFinanceira {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@TenantId
	private UUID tenantId;

	private String nome;

	@Enumerated(EnumType.STRING)
	private TipoDeConta tipo;

	private String banco;

	private String agencia;

	private String numero;

	private BigDecimal saldoInicial;

	private LocalDate dataSaldoInicial;

	private boolean recebimentoPadrao;

	private boolean ativa = true;

	protected ContaFinanceira() {
	}

	public ContaFinanceira(UUID tenantId, String nome, TipoDeConta tipo, BigDecimal saldoInicial, LocalDate dataSaldoInicial) {
		this.tenantId = Objects.requireNonNull(tenantId);
		alterar(nome, tipo, null, null, null);
		this.saldoInicial = Dinheiro.reais(Objects.requireNonNull(saldoInicial, "Informe o saldo inicial (pode ser 0)"));
		this.dataSaldoInicial = Objects.requireNonNull(dataSaldoInicial, "Informe a data do saldo inicial");
	}

	public void alterar(String nome, TipoDeConta tipo, String banco, String agencia, String numero) {
		if (nome == null || nome.isBlank()) {
			throw new IllegalArgumentException("Informe o nome da conta (ex.: Conta corrente Banco X)");
		}
		this.nome = nome.strip();
		this.tipo = Objects.requireNonNull(tipo, "Informe o tipo da conta");
		this.banco = banco;
		this.agencia = agencia;
		this.numero = numero;
	}

	public void definirComoRecebimentoPadrao(boolean padrao) {
		this.recebimentoPadrao = padrao;
	}

	public void desativar() {
		this.ativa = false;
		this.recebimentoPadrao = false;
	}

	/** Lançamentos só podem ser de a partir da data do saldo inicial (antes dela, o saldo inicial já resume tudo). */
	public void conferirData(LocalDate data) {
		if (data.isBefore(dataSaldoInicial)) {
			throw new IllegalArgumentException("A conta " + nome + " começa em " + FormatoBrasileiro.data(dataSaldoInicial)
					+ ": lançamentos anteriores já estão no saldo inicial");
		}
		if (!ativa) {
			throw new IllegalArgumentException("A conta " + nome + " está desativada");
		}
	}

	public UUID getId() { return id; }
	public String getNome() { return nome; }
	public TipoDeConta getTipo() { return tipo; }
	public String getBanco() { return banco; }
	public String getAgencia() { return agencia; }
	public String getNumero() { return numero; }
	public BigDecimal getSaldoInicial() { return saldoInicial; }
	public LocalDate getDataSaldoInicial() { return dataSaldoInicial; }
	public boolean isRecebimentoPadrao() { return recebimentoPadrao; }
	public boolean isAtiva() { return ativa; }

}
