package br.com.condomix.acesso.mandato;

import br.com.condomix.acesso.Cargo;
import br.com.condomix.acesso.Pessoa;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Mandato de uma pessoa num cargo eleito (síndico, subsíndico ou conselheiro).
 *
 * <p>Regras:
 * <ul>
 *   <li>Duração máxima de 2 anos (Código Civil, arts. 1.347 e 1.356).</li>
 *   <li>Vencido sem sucessor, continua valendo, com aviso ({@link SituacaoMandato#PRORROGADO}).</li>
 *   <li>Nunca é apagado: ao terminar, guarda a data e o motivo do encerramento (histórico).</li>
 * </ul>
 */
@Entity
@Table(name = "mandato")
public class Mandato {

	public static final Period DURACAO_MAXIMA = Period.ofYears(2);

	/** A partir de quantos dias antes do fim o mandato aparece como "a vencer". */
	public static final int DIAS_DE_AVISO = 90;

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@TenantId
	private UUID tenantId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pessoa_id")
	private Pessoa pessoa;

	@Enumerated(EnumType.STRING)
	private Cargo cargo;

	private LocalDate inicio;

	private LocalDate fimPrevisto;

	private LocalDate encerradoEm;

	@Enumerated(EnumType.STRING)
	private MotivoEncerramento motivoEncerramento;

	private UUID ataEleicaoId;

	private UUID ataEncerramentoId;

	@Embedded
	private BeneficioDoMandato beneficio;

	private UUID registradoPor;

	@CreationTimestamp
	private Instant criadoEm;

	@UpdateTimestamp
	private Instant atualizadoEm;

	protected Mandato() {
	}

	public Mandato(Pessoa pessoa, Cargo cargo, LocalDate inicio, LocalDate fimPrevisto, UUID ataEleicaoId,
			BeneficioDoMandato beneficio, UUID registradoPor) {
		Objects.requireNonNull(inicio, "inicio");
		Objects.requireNonNull(fimPrevisto, "fimPrevisto");
		if (fimPrevisto.isBefore(inicio)) {
			throw new IllegalArgumentException("O fim do mandato não pode ser antes da posse");
		}
		if (fimPrevisto.isAfter(inicio.plus(DURACAO_MAXIMA).minusDays(1))) {
			throw new IllegalArgumentException("O mandato pode durar no máximo 2 anos (Código Civil, art. 1.347)");
		}
		this.tenantId = pessoa.getTenantId();
		this.pessoa = pessoa;
		this.cargo = Objects.requireNonNull(cargo, "cargo");
		this.inicio = inicio;
		this.fimPrevisto = fimPrevisto;
		this.ataEleicaoId = ataEleicaoId;
		this.beneficio = beneficio;
		this.registradoPor = registradoPor;
	}

	/** Encerra o mandato; {@code ultimoDia} é o último dia em que a pessoa ainda ocupa o cargo. */
	public void encerrar(LocalDate ultimoDia, MotivoEncerramento motivo, UUID ataId) {
		if (encerradoEm != null) {
			throw new IllegalStateException("Este mandato já foi encerrado");
		}
		if (ultimoDia.isBefore(inicio)) {
			throw new IllegalArgumentException("O encerramento não pode ser antes da posse");
		}
		this.encerradoEm = ultimoDia;
		this.motivoEncerramento = Objects.requireNonNull(motivo);
		this.ataEncerramentoId = ataId;
	}

	public SituacaoMandato situacaoEm(LocalDate data) {
		if (data.isBefore(inicio)) {
			return SituacaoMandato.FUTURO;
		}
		if (encerradoEm != null && data.isAfter(encerradoEm)) {
			return SituacaoMandato.ENCERRADO;
		}
		if (data.isAfter(fimPrevisto)) {
			return SituacaoMandato.PRORROGADO;
		}
		return ChronoUnit.DAYS.between(data, fimPrevisto) <= DIAS_DE_AVISO ? SituacaoMandato.A_VENCER : SituacaoMandato.VIGENTE;
	}

	/** Mensagem para o síndico e o conselho quando há algo a fazer (convocar assembleia). */
	public Optional<String> avisoEm(LocalDate data) {
		return situacaoEm(data).aviso(data, fimPrevisto);
	}

	public UUID getId() { return id; }
	public UUID getTenantId() { return tenantId; }
	public Pessoa getPessoa() { return pessoa; }
	public Cargo getCargo() { return cargo; }
	public LocalDate getInicio() { return inicio; }
	public LocalDate getFimPrevisto() { return fimPrevisto; }
	public LocalDate getEncerradoEm() { return encerradoEm; }
	public MotivoEncerramento getMotivoEncerramento() { return motivoEncerramento; }
	public UUID getAtaEleicaoId() { return ataEleicaoId; }
	public UUID getAtaEncerramentoId() { return ataEncerramentoId; }
	public Optional<BeneficioDoMandato> getBeneficio() { return Optional.ofNullable(beneficio); }
	public Instant getCriadoEm() { return criadoEm; }

}
