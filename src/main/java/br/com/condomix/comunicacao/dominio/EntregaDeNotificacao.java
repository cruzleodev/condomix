package br.com.condomix.comunicacao.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A entrega de uma notificação por um canal. Se falhar, tenta de novo mais tarde, até desistir. */
@Entity
@Table(name = "entrega_de_notificacao")
public class EntregaDeNotificacao {

	/** Espera antes de cada nova tentativa; depois da última, desiste. */
	static final List<Duration> ESPERAS = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30),
			Duration.ofHours(2));

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@TenantId
	private UUID tenantId;

	private UUID notificacaoId;

	@Enumerated(EnumType.STRING)
	private Canal canal;

	@Enumerated(EnumType.STRING)
	private SituacaoDaEntrega situacao;

	private int tentativas;

	private Instant proximaTentativaEm;

	private String erro;

	private Instant enviadaEm;

	protected EntregaDeNotificacao() {
	}

	public EntregaDeNotificacao(Notificacao notificacao, Canal canal, Instant agora) {
		this.tenantId = notificacao.getTenantId();
		this.notificacaoId = Objects.requireNonNull(notificacao.getId());
		this.canal = Objects.requireNonNull(canal);
		this.situacao = SituacaoDaEntrega.PENDENTE;
		this.proximaTentativaEm = agora;
	}

	public void enviada(Instant quando) {
		this.tentativas++;
		this.situacao = SituacaoDaEntrega.ENVIADA;
		this.enviadaEm = quando;
		this.erro = null;
	}

	public void descartada(String motivo) {
		this.situacao = SituacaoDaEntrega.DESCARTADA;
		this.erro = limitar(motivo);
	}

	/** Falhou agora: agenda a próxima tentativa ou desiste. */
	public void falhou(String motivo, Instant agora) {
		this.erro = limitar(motivo);
		this.tentativas++;
		if (tentativas > ESPERAS.size()) {
			this.situacao = SituacaoDaEntrega.FALHOU;
		}
		else {
			this.proximaTentativaEm = agora.plus(ESPERAS.get(tentativas - 1));
		}
	}

	private static String limitar(String texto) {
		return texto == null ? null : texto.substring(0, Math.min(texto.length(), 500));
	}

	public UUID getId() { return id; }
	public UUID getTenantId() { return tenantId; }
	public UUID getNotificacaoId() { return notificacaoId; }
	public Canal getCanal() { return canal; }
	public SituacaoDaEntrega getSituacao() { return situacao; }
	public int getTentativas() { return tentativas; }
	public Instant getProximaTentativaEm() { return proximaTentativaEm; }
	public String getErro() { return erro; }

}
