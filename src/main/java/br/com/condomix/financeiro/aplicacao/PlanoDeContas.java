package br.com.condomix.financeiro.aplicacao;

import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.compartilhado.RestricaoDoBanco;
import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.financeiro.dominio.CategoriaFinanceira;
import br.com.condomix.financeiro.dominio.CategoriaPadrao;
import br.com.condomix.financeiro.dominio.ContaFinanceira;
import br.com.condomix.financeiro.dominio.Natureza;
import br.com.condomix.financeiro.dominio.TipoDeConta;
import br.com.condomix.financeiro.persistencia.CategoriaFinanceiraRepository;
import br.com.condomix.financeiro.persistencia.ContaFinanceiraRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Caso de uso: as contas do condomínio e o plano de contas (categorias de receita e despesa).
 * O plano padrão ({@link CategoriaPadrao}) é criado no primeiro uso.
 */
@Service
@Transactional
public class PlanoDeContas {

	private final ContaFinanceiraRepository contas;

	private final CategoriaFinanceiraRepository categorias;

	public PlanoDeContas(ContaFinanceiraRepository contas, CategoriaFinanceiraRepository categorias) {
		this.contas = contas;
		this.categorias = categorias;
	}

	public record DadosDaConta(String nome, TipoDeConta tipo, String banco, String agencia, String numero) {
	}

	public List<CategoriaFinanceira> categorias() {
		garantirCategoriasPadrao();
		return categorias.findAllByOrderByNaturezaAscNomeAsc();
	}

	public CategoriaFinanceira criarCategoria(String nome, Natureza natureza) {
		garantirCategoriasPadrao();
		CategoriaFinanceira nova = Regras.aplicar(() -> new CategoriaFinanceira(TenantAtual.obrigatorio(), nome, natureza));
		return gravarCategoria(nova);
	}

	public CategoriaFinanceira renomearCategoria(UUID id, String nome) {
		CategoriaFinanceira categoria = buscarCategoria(id);
		Regras.aplicar(() -> categoria.renomear(nome));
		return gravarCategoria(categoria);
	}

	public void desativarCategoria(UUID id) {
		CategoriaFinanceira categoria = buscarCategoria(id);
		if (categoria.getCodigo() != null) {
			throw RegraDeNegocioException.invalido("Categorias do sistema não podem ser desativadas (são usadas nos lançamentos automáticos)");
		}
		categoria.desativar();
	}

	/** A categoria do sistema com este código (cria o plano padrão se ainda não existir). */
	public CategoriaFinanceira categoria(CategoriaPadrao codigo) {
		garantirCategoriasPadrao();
		return categorias.findByCodigo(codigo).orElseThrow();
	}

	public CategoriaFinanceira buscarCategoria(UUID id) {
		return categorias.findById(id).orElseThrow(() -> new NaoEncontradoException("Categoria não encontrada"));
	}

	@Transactional(readOnly = true)
	public List<ContaFinanceira> contas() {
		return contas.findAllByOrderByNome();
	}

	/** A primeira conta criada vira a de recebimento padrão. */
	public ContaFinanceira criarConta(DadosDaConta dados, BigDecimal saldoInicial, LocalDate dataSaldoInicial) {
		ContaFinanceira conta = Regras.aplicar(() -> {
			var nova = new ContaFinanceira(TenantAtual.obrigatorio(), dados.nome(), dados.tipo(), saldoInicial, dataSaldoInicial);
			nova.alterar(dados.nome(), dados.tipo(), dados.banco(), dados.agencia(), dados.numero());
			return nova;
		});
		if (contas.findFirstByRecebimentoPadraoTrue().isEmpty()) {
			conta.definirComoRecebimentoPadrao(true);
		}
		return gravarConta(conta);
	}

	public ContaFinanceira alterarConta(UUID id, DadosDaConta dados) {
		ContaFinanceira conta = buscarConta(id);
		Regras.aplicar(() -> conta.alterar(dados.nome(), dados.tipo(), dados.banco(), dados.agencia(), dados.numero()));
		return gravarConta(conta);
	}

	/** Os pagamentos das cobranças passam a entrar nesta conta. */
	public ContaFinanceira definirRecebimentoPadrao(UUID id) {
		ContaFinanceira conta = buscarConta(id);
		if (!conta.isAtiva()) {
			throw RegraDeNegocioException.invalido("A conta está desativada");
		}
		contas.findFirstByRecebimentoPadraoTrue().ifPresent(atual -> atual.definirComoRecebimentoPadrao(false));
		contas.flush();
		conta.definirComoRecebimentoPadrao(true);
		return conta;
	}

	public void desativarConta(UUID id) {
		buscarConta(id).desativar();
	}

	/**
	 * A conta onde entram os pagamentos das cobranças. Se o condomínio ainda não cadastrou nenhuma,
	 * cria a "Conta principal" com saldo zero a partir de {@code inicio}.
	 */
	public ContaFinanceira contaDeRecebimento(LocalDate inicio) {
		return contas.findFirstByRecebimentoPadraoTrue().orElseGet(() -> {
			var principal = new ContaFinanceira(TenantAtual.obrigatorio(), "Conta principal", TipoDeConta.CONTA_CORRENTE,
					BigDecimal.ZERO, inicio.withDayOfMonth(1));
			principal.definirComoRecebimentoPadrao(true);
			return contas.save(principal);
		});
	}

	public ContaFinanceira buscarConta(UUID id) {
		return contas.findById(id).orElseThrow(() -> new NaoEncontradoException("Conta não encontrada"));
	}

	private void garantirCategoriasPadrao() {
		Set<CategoriaPadrao> existentes = categorias.findAll().stream().map(CategoriaFinanceira::getCodigo)
				.filter(c -> c != null).collect(Collectors.toCollection(() -> EnumSet.noneOf(CategoriaPadrao.class)));
		UUID tenant = TenantAtual.obrigatorio();
		for (CategoriaPadrao padrao : CategoriaPadrao.values()) {
			if (!existentes.contains(padrao)) {
				categorias.save(CategoriaFinanceira.padrao(tenant, padrao));
			}
		}
	}

	private CategoriaFinanceira gravarCategoria(CategoriaFinanceira categoria) {
		try {
			return categorias.saveAndFlush(categoria);
		}
		catch (DataIntegrityViolationException e) {
			if (RestricaoDoBanco.violou(e, "categoria_financeira_tenant_id_nome_key")) {
				throw RegraDeNegocioException.conflito("Já existe uma categoria com este nome");
			}
			throw e;
		}
	}

	private ContaFinanceira gravarConta(ContaFinanceira conta) {
		try {
			return contas.saveAndFlush(conta);
		}
		catch (DataIntegrityViolationException e) {
			if (RestricaoDoBanco.violou(e, "conta_financeira_tenant_id_nome_key")) {
				throw RegraDeNegocioException.conflito("Já existe uma conta com este nome");
			}
			throw e;
		}
	}

}
