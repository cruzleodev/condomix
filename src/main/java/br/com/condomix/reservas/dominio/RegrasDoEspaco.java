package br.com.condomix.reservas.dominio;

import jakarta.persistence.Embeddable;

import java.util.Objects;

/**
 * Os números das regras de reserva de um espaço. Classe, e não {@code record}: o Hibernate troca a ordem das
 * colunas de um {@code record} embutido.
 */
@Embeddable
public class RegrasDoEspaco {

	/** Reservar com pelo menos tantos dias de antecedência (0: pode ser no dia). */
	private int antecedenciaMinimaDias;

	/** E no máximo tantos dias à frente. */
	private int antecedenciaMaximaDias;

	/** Reservas por unidade, por mês, neste espaço; vazio: sem limite. */
	private Integer limitePorMes;

	/** Unidade com cobrança vencida não reserva. */
	private boolean bloqueiaInadimplente;

	/** Cancelar até tantos dias antes não tem custo; depois, o aluguel continua devido. */
	private int diasParaCancelar;

	protected RegrasDoEspaco() {
	}

	public RegrasDoEspaco(int antecedenciaMinimaDias, int antecedenciaMaximaDias, Integer limitePorMes,
			boolean bloqueiaInadimplente, int diasParaCancelar) {
		if (antecedenciaMinimaDias < 0 || diasParaCancelar < 0) {
			throw new IllegalArgumentException("Os prazos não podem ser negativos");
		}
		if (antecedenciaMaximaDias < antecedenciaMinimaDias) {
			throw new IllegalArgumentException("A antecedência máxima não pode ser menor que a mínima");
		}
		if (limitePorMes != null && limitePorMes <= 0) {
			throw new IllegalArgumentException("O limite por mês deve ser maior que zero (ou vazio, para sem limite)");
		}
		this.antecedenciaMinimaDias = antecedenciaMinimaDias;
		this.antecedenciaMaximaDias = antecedenciaMaximaDias;
		this.limitePorMes = limitePorMes;
		this.bloqueiaInadimplente = bloqueiaInadimplente;
		this.diasParaCancelar = diasParaCancelar;
	}

	/** O padrão de um espaço novo: até 90 dias à frente, sem limite, bloqueia inadimplente, cancela até o dia. */
	public static RegrasDoEspaco padrao() {
		return new RegrasDoEspaco(0, 90, null, true, 0);
	}

	public int antecedenciaMinimaDias() { return antecedenciaMinimaDias; }
	public int antecedenciaMaximaDias() { return antecedenciaMaximaDias; }
	public Integer limitePorMes() { return limitePorMes; }
	public boolean bloqueiaInadimplente() { return bloqueiaInadimplente; }
	public int diasParaCancelar() { return diasParaCancelar; }

	@Override
	public boolean equals(Object o) {
		return o instanceof RegrasDoEspaco r && antecedenciaMinimaDias == r.antecedenciaMinimaDias
				&& antecedenciaMaximaDias == r.antecedenciaMaximaDias && Objects.equals(limitePorMes, r.limitePorMes)
				&& bloqueiaInadimplente == r.bloqueiaInadimplente && diasParaCancelar == r.diasParaCancelar;
	}

	@Override
	public int hashCode() {
		return Objects.hash(antecedenciaMinimaDias, antecedenciaMaximaDias, limitePorMes, bloqueiaInadimplente, diasParaCancelar);
	}

}
