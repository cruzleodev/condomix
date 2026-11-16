package br.com.condomix.acesso.pessoa;

import br.com.condomix.acesso.Pessoa;
import br.com.condomix.acesso.TipoVinculo;
import br.com.condomix.acesso.Unidade;
import br.com.condomix.acesso.VinculoUnidade;
import br.com.condomix.acesso.persistencia.PessoaRepository;
import br.com.condomix.acesso.persistencia.UnidadeRepository;
import br.com.condomix.acesso.persistencia.VinculoUnidadeRepository;
import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.compartilhado.RestricaoDoBanco;
import br.com.condomix.compartilhado.TenantAtual;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;

/** Caso de uso: o síndico cadastra pessoas e as liga (ou desliga) das unidades. */
@Service
@Transactional
public class CadastroDePessoas {

	private final PessoaRepository pessoas;

	private final UnidadeRepository unidades;

	private final VinculoUnidadeRepository vinculos;

	private final Clock relogio;

	public CadastroDePessoas(PessoaRepository pessoas, UnidadeRepository unidades, VinculoUnidadeRepository vinculos,
			Clock relogio) {
		this.pessoas = pessoas;
		this.unidades = unidades;
		this.vinculos = vinculos;
		this.relogio = relogio;
	}

	/** @param documento CPF ou CNPJ, com ou sem pontuação; @param telefone com DDD */
	public record DadosDaPessoa(String nome, String documento, String email, String telefone) {
	}

	public Pessoa criar(DadosDaPessoa dados) {
		Pessoa pessoa = regra(() -> aplicar(new Pessoa(TenantAtual.obrigatorio(), dados.nome()), dados));
		return gravar(() -> pessoas.saveAndFlush(pessoa));
	}

	public Pessoa alterar(UUID id, DadosDaPessoa dados) {
		Pessoa pessoa = buscar(id);
		regra(() -> {
			pessoa.alterarNome(dados.nome());
			return aplicar(pessoa, dados);
		});
		return gravar(() -> pessoas.saveAndFlush(pessoa));
	}

	/** @param inicio vazio = hoje */
	public VinculoUnidade vincular(UUID pessoaId, UUID unidadeId, TipoVinculo tipo, LocalDate inicio) {
		Pessoa pessoa = buscar(pessoaId);
		Unidade unidade = unidades.findById(unidadeId)
				.orElseThrow(() -> new NaoEncontradoException("Unidade não encontrada"));
		if (!unidade.isAtivo()) {
			throw RegraDeNegocioException.invalido("A unidade " + unidade.descricao() + " está desativada");
		}
		LocalDate desde = inicio == null ? LocalDate.now(relogio) : inicio;
		VinculoUnidade vinculo = regra(() -> new VinculoUnidade(pessoa, unidade, tipo, desde));
		return gravar(() -> vinculos.saveAndFlush(vinculo));
	}

	/** {@code fim} é o último dia (ex.: mudança, venda do imóvel). O histórico continua guardado. */
	public void encerrarVinculo(UUID pessoaId, UUID vinculoId, LocalDate fim) {
		VinculoUnidade vinculo = vinculos.findById(vinculoId)
				.filter(v -> v.getPessoa().getId().equals(pessoaId))
				.orElseThrow(() -> new NaoEncontradoException("Vínculo não encontrado"));
		try {
			vinculo.encerrar(fim);
		}
		catch (IllegalArgumentException | IllegalStateException e) {
			throw RegraDeNegocioException.invalido(e.getMessage());
		}
	}

	private Pessoa buscar(UUID id) {
		return pessoas.findById(id).orElseThrow(() -> new NaoEncontradoException("Pessoa não encontrada"));
	}

	private static Pessoa aplicar(Pessoa pessoa, DadosDaPessoa dados) {
		pessoa.definirDocumento(dados.documento());
		pessoa.alterarContato(dados.email(), dados.telefone());
		return pessoa;
	}

	private static <T> T regra(Supplier<T> acao) {
		try {
			return acao.get();
		}
		catch (IllegalArgumentException | NullPointerException e) {
			throw RegraDeNegocioException.invalido(e.getMessage());
		}
	}

	private static <T> T gravar(Supplier<T> acao) {
		try {
			return acao.get();
		}
		catch (DataIntegrityViolationException e) {
			if (RestricaoDoBanco.violou(e, "pessoa_tenant_id_documento_key")) {
				throw RegraDeNegocioException.conflito("Já existe uma pessoa com este CPF/CNPJ neste condomínio");
			}
			if (RestricaoDoBanco.violou(e, "ux_vinculo_unidade_em_aberto")) {
				throw RegraDeNegocioException.conflito("Esta pessoa já tem este vínculo com a unidade");
			}
			throw e;
		}
	}

}
