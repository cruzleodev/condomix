package br.com.condomix.faturamento.dominio;

import br.com.condomix.pagamentos.AmbienteDoGateway;
import br.com.condomix.pagamentos.TipoDeGateway;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A conta do Condomix no gateway, que recebe as assinaturas dos condomínios. Só existe uma (a tabela garante).
 * Dado da plataforma: sem {@code tenant_id}. A chave de API fica cifrada pelo {@code Cofre}.
 */
@Entity
@Table(name = "conta_de_recebimento")
public class ContaDeRecebimento {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Enumerated(EnumType.STRING)
	private TipoDeGateway gateway;

	@Enumerated(EnumType.STRING)
	private AmbienteDoGateway ambiente;

	private String chaveCifrada;

	private String tokenAvisoHash;

	private boolean ativa;

	private Instant configuradaEm;

	private UUID configuradaPor;

	protected ContaDeRecebimento() {
	}

	public ContaDeRecebimento(TipoDeGateway gateway, AmbienteDoGateway ambiente, String chaveCifrada,
			String tokenAvisoHash, Instant quando, UUID por) {
		configurar(gateway, ambiente, chaveCifrada, tokenAvisoHash, quando, por);
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

	/** Para de gerar faturas novas. As já emitidas continuam valendo e sendo baixadas. */
	public void desativar() {
		this.ativa = false;
	}

	public UUID getId() { return id; }
	public TipoDeGateway getGateway() { return gateway; }
	public AmbienteDoGateway getAmbiente() { return ambiente; }
	public String getChaveCifrada() { return chaveCifrada; }
	public String getTokenAvisoHash() { return tokenAvisoHash; }
	public boolean isAtiva() { return ativa; }
	public Instant getConfiguradaEm() { return configuradaEm; }

}
