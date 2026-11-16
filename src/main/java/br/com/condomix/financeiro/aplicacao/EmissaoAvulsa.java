package br.com.condomix.financeiro.aplicacao;

import br.com.condomix.acesso.CadastroParaCobranca;
import br.com.condomix.acesso.CadastroParaCobranca.UnidadeCobravel;
import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.financeiro.dominio.Cobranca;
import br.com.condomix.financeiro.dominio.Dinheiro;
import br.com.condomix.financeiro.dominio.ItemDeCobranca;
import br.com.condomix.financeiro.dominio.OrigemCobranca;
import br.com.condomix.financeiro.dominio.TipoDeItem;
import br.com.condomix.financeiro.persistencia.CobrancaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Caso de uso: cobrança avulsa para uma unidade (multa disciplinar, aluguel de espaço, consumo, outro).
 * Será usado também pelo módulo de reservas para o aluguel do salão de festas.
 */
@Service
@Transactional
public class EmissaoAvulsa {

	private static final Set<TipoDeItem> PERMITIDOS = EnumSet.of(TipoDeItem.MULTA_DISCIPLINAR,
			TipoDeItem.RESERVA_DE_ESPACO, TipoDeItem.CONSUMO, TipoDeItem.TAXA_EXTRA, TipoDeItem.OUTRO);

	private final CadastroParaCobranca cadastro;

	private final ConfiguracaoDeCobrancas configuracoes;

	private final CobrancaRepository cobrancas;

	private final Clock relogio;

	public EmissaoAvulsa(CadastroParaCobranca cadastro, ConfiguracaoDeCobrancas configuracoes,
			CobrancaRepository cobrancas, Clock relogio) {
		this.cadastro = cadastro;
		this.configuracoes = configuracoes;
		this.cobrancas = cobrancas;
		this.relogio = relogio;
	}

	public Cobranca emitir(UUID unidadeId, TipoDeItem tipo, String descricao, BigDecimal valor, LocalDate vencimento,
			UUID criadaPor) {
		if (!PERMITIDOS.contains(tipo)) {
			throw RegraDeNegocioException.invalido("Tipo não permitido em cobrança avulsa: use " + PERMITIDOS);
		}
		if (valor == null || valor.signum() <= 0) {
			throw RegraDeNegocioException.invalido("O valor deve ser maior que zero");
		}
		UnidadeCobravel unidade = cadastro.unidadesAtivas(LocalDate.now(relogio)).stream()
				.filter(u -> u.id().equals(unidadeId)).findFirst()
				.orElseThrow(() -> new NaoEncontradoException("Unidade não encontrada ou desativada"));
		var item = new ItemDeCobranca(tipo, descricao, Dinheiro.reais(valor));
		return cobrancas.save(Regras.aplicar(() -> new Cobranca(new Cobranca.Emissao(TenantAtual.obrigatorio(),
				unidade.id(), unidade.responsavel() == null ? null : unidade.responsavel().pessoaId(),
				OrigemCobranca.AVULSA, null, null, descricao, vencimento, List.of(item), configuracoes.atual(), criadaPor))));
	}

}
