package br.com.condomix.comunicacao.dominio;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Se a pessoa quer receber um grupo por um canal. Vale em todos os condomínios dela (sem {@code tenant_id}).
 * Sem preferência gravada, recebe.
 */
@Entity
@Table(name = "preferencia_de_notificacao")
public class PreferenciaDeNotificacao {

	@EmbeddedId
	private Chave chave;

	private boolean ativo;

	protected PreferenciaDeNotificacao() {
	}

	public PreferenciaDeNotificacao(UUID usuarioId, GrupoDeNotificacao grupo, Canal canal, boolean ativo) {
		this.chave = new Chave(usuarioId, grupo, canal);
		this.ativo = ativo;
	}

	public void definir(boolean ativo) {
		this.ativo = ativo;
	}

	public GrupoDeNotificacao getGrupo() { return chave.grupo; }
	public Canal getCanal() { return chave.canal; }
	public boolean isAtivo() { return ativo; }

	/** Classe, e não {@code record}: o Hibernate troca a ordem das colunas de um {@code record} embutido. */
	@Embeddable
	public static class Chave implements Serializable {

		private UUID usuarioId;

		@Enumerated(EnumType.STRING)
		private GrupoDeNotificacao grupo;

		@Enumerated(EnumType.STRING)
		private Canal canal;

		protected Chave() {
		}

		public Chave(UUID usuarioId, GrupoDeNotificacao grupo, Canal canal) {
			this.usuarioId = Objects.requireNonNull(usuarioId);
			this.grupo = Objects.requireNonNull(grupo);
			this.canal = Objects.requireNonNull(canal);
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Chave c && usuarioId.equals(c.usuarioId) && grupo == c.grupo && canal == c.canal;
		}

		@Override
		public int hashCode() {
			return Objects.hash(usuarioId, grupo, canal);
		}

	}

}
