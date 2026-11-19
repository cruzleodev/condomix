package br.com.condomix.financeiro.dominio;

import br.com.condomix.pagamentos.AmbienteDoGateway;
import br.com.condomix.pagamentos.TipoDeGateway;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A conta do condomínio no gateway de pagamentos. Configurada pelo administrador da plataforma.
 * Guarda a chave de API cifrada e só a impressão digital do token que o gateway manda no aviso (webhook).
 */
@Entity
@Table(name = "conta_gateway")
public class ContaNoGateway {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@TenantId
	private UUID tenantId;

	@Enumerated(EnumType.STRING)
	private TipoDeGateway gateway;

	@Enumerated(EnumType.STRING)
	private AmbienteDoGateway ambiente;

	private String chaveCifrada;

	private String tokenAvisoHash;

	private boolean ativa;

	private Instant configuradaEm;

	private UUID configuradaPor;

	protected ContaNoGateway() {
	}

	public ContaNoGateway(UUID tenantId) {
		this.tenantId = Objects.requireNonNull(tenantId);
	}

	/** Liga (ou troca) a conta. O token do aviso muda a cada configuração. */
	public void configurar(TipoDeGateway gateway, AmbienteDoGateway ambiente, String chaveCifrada, String tokenAvisoHash,
			Instant quando, UUID por) {
		this.gateway = Objects.requireNonNull(gateway);
		this.ambiente = Objects.requireNonNull(ambiente);
		this.chaveCifrada = Objects.requireNonNull(chaveCifrada);
		this.tokenAvisoHash = Objects.requireNonNull(tokenAvisoHash);
		this.configuradaEm = Objects.requireNonNull(quando);
		this.configuradaPor = por;
		this.ativa = true;
	}

	/** Para de emitir boletos novos. Os já emitidos continuam valendo e sendo baixados. */
	public void desativar() {
		this.ativa = false;
	}

	public UUID getId() { return id; }
	public UUID getTenantId() { return tenantId; }
	public TipoDeGateway getGateway() { return gateway; }
	public AmbienteDoGateway getAmbiente() { return ambiente; }
	public String getChaveCifrada() { return chaveCifrada; }
	public String getTokenAvisoHash() { return tokenAvisoHash; }
	public boolean isAtiva() { return ativa; }
	public Instant getConfiguradaEm() { return configuradaEm; }

}
