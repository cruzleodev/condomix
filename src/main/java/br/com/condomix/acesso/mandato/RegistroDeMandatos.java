package br.com.condomix.acesso.mandato;

import br.com.condomix.acesso.Cargo;
import br.com.condomix.acesso.Pessoa;
import br.com.condomix.acesso.persistencia.MandatoRepository;
import br.com.condomix.acesso.persistencia.PessoaRepository;
import br.com.condomix.acesso.persistencia.UnidadeRepository;
import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Caso de uso: registrar o resultado de uma assembleia (eleição) ou o encerramento antecipado
 * de um mandato (renúncia, destituição). O sistema registra; quem decide é a assembleia.
 */
@Service
@Transactional
public class RegistroDeMandatos {

	private final MandatoRepository mandatos;

	private final PessoaRepository pessoas;

	private final UnidadeRepository unidades;

	public RegistroDeMandatos(MandatoRepository mandatos, PessoaRepository pessoas, UnidadeRepository unidades) {
		this.mandatos = mandatos;
		this.pessoas = pessoas;
		this.unidades = unidades;
	}

	public record NovoMandato(UUID pessoaId, Cargo cargo, LocalDate inicio, LocalDate fimPrevisto,
			UUID ataEleicaoId, BigDecimal descontoPercentual, UUID unidadeBeneficiadaId) {
	}

	/**
	 * Registra a eleição. Para síndico e subsíndico, o mandato em aberto do antecessor é encerrado
	 * automaticamente no dia anterior à posse (motivo SUCESSAO).
	 */
	public Mandato registrar(NovoMandato pedido, UUID registradoPor) {
		Pessoa pessoa = pessoas.findById(pedido.pessoaId())
				.orElseThrow(() -> new NaoEncontradoException("Pessoa não encontrada neste condomínio"));
		Mandato novo = criar(pedido, pessoa, beneficio(pedido), registradoPor);

		if (pedido.cargo().ocupanteUnico()) {
			encerrarAntecessor(pedido);
		}
		else if (mandatos.emAberto(pedido.cargo()).stream().anyMatch(m -> m.getPessoa().getId().equals(pessoa.getId()))) {
			throw RegraDeNegocioException.conflito(pessoa.getNome() + " já tem um mandato de "
					+ pedido.cargo().rotulo().toLowerCase() + " em aberto");
		}
		return gravar(novo);
	}

	/** Encerramento antecipado. {@code ultimoDia} é o último dia em que a pessoa ainda ocupa o cargo. */
	public Mandato encerrar(UUID mandatoId, LocalDate ultimoDia, MotivoEncerramento motivo, UUID ataId) {
		if (motivo == MotivoEncerramento.SUCESSAO) {
			throw RegraDeNegocioException.invalido("A sucessão é registrada cadastrando o novo mandato");
		}
		Mandato mandato = mandatos.findById(mandatoId)
				.orElseThrow(() -> new NaoEncontradoException("Mandato não encontrado"));
		try {
			mandato.encerrar(ultimoDia, motivo, ataId);
		}
		catch (IllegalArgumentException | IllegalStateException e) {
			throw RegraDeNegocioException.invalido(e.getMessage());
		}
		return gravar(mandato);
	}

	private void encerrarAntecessor(NovoMandato pedido) {
		for (Mandato atual : mandatos.emAberto(pedido.cargo())) {
			if (!atual.getInicio().isBefore(pedido.inicio())) {
				throw RegraDeNegocioException.conflito("Já existe um mandato de " + pedido.cargo().rotulo().toLowerCase()
						+ " com posse em " + atual.getInicio() + ". A posse do novo deve ser depois dessa data.");
			}
			atual.encerrar(pedido.inicio().minusDays(1), MotivoEncerramento.SUCESSAO, pedido.ataEleicaoId());
		}
		// O Hibernate grava inclusões antes de alterações; sem este flush, o banco veria dois síndicos ao mesmo tempo.
		mandatos.flush();
	}

	private BeneficioDoMandato beneficio(NovoMandato pedido) {
		if (pedido.descontoPercentual() == null && pedido.unidadeBeneficiadaId() == null) {
			return null;
		}
		if (pedido.descontoPercentual() == null || pedido.unidadeBeneficiadaId() == null) {
			throw RegraDeNegocioException.invalido("Informe o percentual de desconto e a unidade beneficiada juntos");
		}
		if (unidades.findById(pedido.unidadeBeneficiadaId()).isEmpty()) {
			throw RegraDeNegocioException.invalido("Unidade beneficiada não encontrada neste condomínio");
		}
		try {
			return new BeneficioDoMandato(pedido.descontoPercentual(), pedido.unidadeBeneficiadaId());
		}
		catch (IllegalArgumentException e) {
			throw RegraDeNegocioException.invalido(e.getMessage());
		}
	}

	private static Mandato criar(NovoMandato pedido, Pessoa pessoa, BeneficioDoMandato beneficio, UUID registradoPor) {
		try {
			return new Mandato(pessoa, pedido.cargo(), pedido.inicio(), pedido.fimPrevisto(), pedido.ataEleicaoId(),
					beneficio, registradoPor);
		}
		catch (IllegalArgumentException | NullPointerException e) {
			throw RegraDeNegocioException.invalido(e.getMessage());
		}
	}

	/** Grava já enviando ao banco, para transformar as recusas do banco em mensagens claras. */
	private Mandato gravar(Mandato mandato) {
		try {
			return mandatos.saveAndFlush(mandato);
		}
		catch (DataIntegrityViolationException e) {
			String causa = String.valueOf(e.getMostSpecificCause().getMessage());
			if (causa.contains("ex_mandato_cargo_unico")) {
				throw RegraDeNegocioException.conflito("O período se sobrepõe ao de outro "
						+ mandato.getCargo().rotulo().toLowerCase() + ": só pode haver um por vez");
			}
			if (causa.contains("ata_")) {
				throw RegraDeNegocioException.invalido("A ata informada não é um documento deste condomínio");
			}
			throw e;
		}
	}

}
