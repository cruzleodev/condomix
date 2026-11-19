package br.com.condomix.acesso.suporte;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro de auditoria: um administrador da plataforma acessou os dados de um condomínio (LGPD).
 * Não tem {@code @TenantId}: é um dado da plataforma, consultado pelo administrador para todos os condomínios.
 */
@Entity
@Table(name = "acesso_suporte")
public class AcessoDeSuporte {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	private UUID administradorId;

	private UUID condominioId;

	private String motivo;

	private Instant inicio;

	private Instant expiraEm;

	/** Encerrado pelo administrador antes do prazo. */
	private Instant encerradoEm;

	protected AcessoDeSuporte() {
	}

	public AcessoDeSuporte(UUID administradorId, UUID condominioId, String motivo, Instant inicio, Instant expiraEm) {
		if (motivo == null || motivo.strip().length() < 10) {
			throw new IllegalArgumentException("Explique o motivo do acesso (pelo menos 10 caracteres)");
		}
		this.administradorId = Objects.requireNonNull(administradorId);
		this.condominioId = Objects.requireNonNull(condominioId);
		this.motivo = motivo.strip();
		this.inicio = inicio;
		this.expiraEm = expiraEm;
	}

	/** Encerra antes do prazo. Sem efeito se já terminou (encerrado ou expirado). */
	public void encerrar(Instant agora) {
		if (encerradoEm == null && agora.isBefore(expiraEm)) {
			this.encerradoEm = agora;
		}
	}

	public UUID getId() { return id; }
	public UUID getAdministradorId() { return administradorId; }
	public UUID getCondominioId() { return condominioId; }
	public String getMotivo() { return motivo; }
	public Instant getInicio() { return inicio; }
	public Instant getExpiraEm() { return expiraEm; }
	public Instant getEncerradoEm() { return encerradoEm; }

}
