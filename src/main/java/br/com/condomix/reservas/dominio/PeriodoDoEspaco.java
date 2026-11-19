package br.com.condomix.reservas.dominio;

import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;

import java.time.LocalTime;
import java.util.Objects;

/**
 * Um horário que pode ser reservado (ex.: "Noite", das 18h às 23h). Termina no mesmo dia.
 * Classe, e não {@code record}: o Hibernate troca a ordem das colunas de um {@code record} embutido em coleção.
 */
@Embeddable
public class PeriodoDoEspaco {

	private String nome;

	@Convert(converter = HoraDoDia.class)
	private LocalTime inicio;

	@Convert(converter = HoraDoDia.class)
	private LocalTime fim;

	protected PeriodoDoEspaco() {
	}

	public PeriodoDoEspaco(String nome, LocalTime inicio, LocalTime fim) {
		if (nome == null || nome.isBlank()) {
			throw new IllegalArgumentException("Dê um nome ao horário (ex.: Dia inteiro, Noite)");
		}
		this.nome = nome.strip();
		this.inicio = Objects.requireNonNull(inicio, "Informe o início do horário");
		this.fim = Objects.requireNonNull(fim, "Informe o fim do horário");
		if (!fim.isAfter(inicio)) {
			throw new IllegalArgumentException("O horário \"" + this.nome + "\" precisa terminar depois de começar, no mesmo dia");
		}
	}

	public boolean sobrepoe(LocalTime outroInicio, LocalTime outroFim) {
		return inicio.isBefore(outroFim) && outroInicio.isBefore(fim);
	}

	public String nome() { return nome; }
	public LocalTime inicio() { return inicio; }
	public LocalTime fim() { return fim; }

	@Override
	public boolean equals(Object o) {
		return o instanceof PeriodoDoEspaco p && nome.equals(p.nome) && inicio.equals(p.inicio) && fim.equals(p.fim);
	}

	@Override
	public int hashCode() {
		return Objects.hash(nome, inicio, fim);
	}

}
