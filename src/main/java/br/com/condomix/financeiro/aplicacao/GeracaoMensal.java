package br.com.condomix.financeiro.aplicacao;

import br.com.condomix.compartilhado.FormatoBrasileiro;
import br.com.condomix.acesso.CadastroParaCobranca;
import br.com.condomix.acesso.CadastroParaCobranca.BeneficioDeMandato;
import br.com.condomix.acesso.CadastroParaCobranca.UnidadeCobravel;
import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.financeiro.dominio.Cobranca;
import br.com.condomix.financeiro.dominio.CobrancaCancelada;
import br.com.condomix.financeiro.dominio.CobrancasEmitidas;
import br.com.condomix.financeiro.dominio.ConfiguracaoDaCobranca;
import br.com.condomix.financeiro.dominio.Dinheiro;
import br.com.condomix.financeiro.dominio.GeracaoDeCobranca;
import br.com.condomix.financeiro.dominio.ItemDeCobranca;
import br.com.condomix.financeiro.dominio.OrigemCobranca;
import br.com.condomix.financeiro.dominio.StatusCobranca;
import br.com.condomix.financeiro.dominio.TipoDeItem;
import br.com.condomix.financeiro.dominio.TipoDeRateio;
import br.com.condomix.financeiro.persistencia.CobrancaRepository;
import br.com.condomix.financeiro.persistencia.GeracaoDeCobrancaRepository;
import br.com.condomix.financeiro.rateio.CalculoDoRateio;
import br.com.condomix.financeiro.rateio.DistribuicaoDeCentavos;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Caso de uso: gerar a taxa do mês para todas as unidades ativas.
 *
 * <p>Composição de cada cobrança:
 * <ol>
 *   <li>taxa ordinária, pela regra de rateio do condomínio;</li>
 *   <li>fundo de reserva: percentual sobre a taxa ordinária;</li>
 *   <li>taxas extras do mês (obras etc.), cada uma rateada igual ou pela fração ideal;</li>
 *   <li>desconto de mandato (síndico, subsíndico...): percentual sobre a taxa ordinária, proporcional
 *       aos dias do mês em que o mandato valeu.</li>
 * </ol>
 * Unidades com total zero (ex.: isenção de 100% sem fundo de reserva) não recebem cobrança.
 * Dá para simular antes de gerar.
 */
@Service
@Transactional
public class GeracaoMensal {

	private final CadastroParaCobranca cadastro;

	private final ConfiguracaoDeCobrancas configuracoes;

	private final GeracaoDeCobrancaRepository geracoes;

	private final CobrancaRepository cobrancas;

	private final Map<TipoDeRateio, CalculoDoRateio> rateios;

	private final ApplicationEventPublisher eventos;

	private final Clock relogio;

	public GeracaoMensal(CadastroParaCobranca cadastro, ConfiguracaoDeCobrancas configuracoes,
			GeracaoDeCobrancaRepository geracoes, CobrancaRepository cobrancas, List<CalculoDoRateio> rateios,
			ApplicationEventPublisher eventos, Clock relogio) {
		this.cadastro = cadastro;
		this.configuracoes = configuracoes;
		this.geracoes = geracoes;
		this.cobrancas = cobrancas;
		this.rateios = rateios.stream().collect(Collectors.toMap(CalculoDoRateio::tipo, Function.identity(),
				(a, b) -> a, () -> new EnumMap<>(TipoDeRateio.class)));
		this.eventos = eventos;
		this.relogio = relogio;
	}

	/** @param rateio IGUALITARIO ou FRACAO_IDEAL */
	public record TaxaExtra(String descricao, BigDecimal valorTotal, TipoDeRateio rateio) {
	}

	/** @param valorOrdinariaTotal obrigatório nos rateios igualitário e por fração ideal; ignorado no por grupo
	 *  @param vencimento vazio = dia de vencimento da configuração, no mês da competência */
	public record Pedido(YearMonth competencia, BigDecimal valorOrdinariaTotal, LocalDate vencimento,
			List<TaxaExtra> taxasExtras) {
	}

	public record Linha(UUID unidadeId, String unidade, String responsavel, List<ItemDeCobranca> itens, BigDecimal total) {
	}

	public record ResultadoDaGeracao(UUID geracaoId, YearMonth competencia, LocalDate vencimento, TipoDeRateio rateio,
			List<Linha> cobrancas, List<String> isentas, BigDecimal totalGeral, List<String> avisos) {
	}

	@Transactional(readOnly = true)
	public ResultadoDaGeracao simular(Pedido pedido) {
		return calcular(pedido, configuracoes.atual());
	}

	public ResultadoDaGeracao gerar(Pedido pedido, UUID geradaPor) {
		if (geracoes.existeValida(pedido.competencia().atDay(1))) {
			throw RegraDeNegocioException.conflito("A taxa de " + FormatoBrasileiro.mes(pedido.competencia())
					+ " já foi gerada. Para gerar de novo, cancele a geração anterior.");
		}
		ConfiguracaoDaCobranca regras = configuracoes.atual();
		ResultadoDaGeracao calculo = calcular(pedido, regras);
		UUID tenant = TenantAtual.obrigatorio();
		GeracaoDeCobranca geracao = geracoes.save(new GeracaoDeCobranca(tenant, pedido.competencia(),
				regras.getTipoRateio(), pedido.valorOrdinariaTotal(), geradaPor));
		Map<UUID, UnidadeCobravel> unidades = cadastro.unidadesAtivas(pedido.competencia().atDay(1)).stream()
				.collect(Collectors.toMap(UnidadeCobravel::id, Function.identity()));
		List<UUID> emitidas = new ArrayList<>();
		for (Linha linha : calculo.cobrancas()) {
			var responsavel = unidades.get(linha.unidadeId()).responsavel();
			emitidas.add(cobrancas.save(new Cobranca(new Cobranca.Emissao(tenant, linha.unidadeId(),
					responsavel == null ? null : responsavel.pessoaId(), OrigemCobranca.GERACAO_MENSAL, geracao.getId(),
					pedido.competencia(), "Taxa condominial " + FormatoBrasileiro.mes(pedido.competencia()), calculo.vencimento(), linha.itens(),
					regras, geradaPor))).getId());
		}
		eventos.publishEvent(new CobrancasEmitidas(emitidas));
		return new ResultadoDaGeracao(geracao.getId(), calculo.competencia(), calculo.vencimento(), calculo.rateio(),
				calculo.cobrancas(), calculo.isentas(), calculo.totalGeral(), calculo.avisos());
	}

	/** Cancela a geração e as cobranças dela. Não é possível se alguma já foi paga (estorne antes). */
	public void cancelar(UUID geracaoId, String motivo) {
		GeracaoDeCobranca geracao = geracoes.findById(geracaoId)
				.filter(g -> g.getCanceladaEm() == null)
				.orElseThrow(() -> new NaoEncontradoException("Geração não encontrada ou já cancelada"));
		List<Cobranca> daGeracao = cobrancas.findByGeracaoId(geracaoId);
		if (daGeracao.stream().anyMatch(c -> c.getStatus() == StatusCobranca.PAGA)) {
			throw RegraDeNegocioException.conflito("Há cobranças pagas nesta geração: estorne os pagamentos antes de cancelar");
		}
		Instant agora = Instant.now(relogio);
		daGeracao.stream().filter(c -> c.getStatus() == StatusCobranca.ABERTA).forEach(c -> {
			c.cancelar("Geração cancelada" + (motivo == null ? "" : ": " + motivo), agora);
			eventos.publishEvent(new CobrancaCancelada(c.getId()));
		});
		geracao.cancelar(agora);
	}

	@Transactional(readOnly = true)
	public List<GeracaoDeCobranca> historico() {
		return geracoes.historico();
	}

	private ResultadoDaGeracao calcular(Pedido pedido, ConfiguracaoDaCobranca regras) {
		YearMonth mes = pedido.competencia();
		LocalDate vencimento = pedido.vencimento() != null ? pedido.vencimento() : mes.atDay(regras.getDiaVencimento());
		List<UnidadeCobravel> unidades = cadastro.unidadesAtivas(mes.atDay(1));
		if (unidades.isEmpty()) {
			throw RegraDeNegocioException.invalido("Não há unidades ativas para cobrar");
		}
		List<String> avisos = new ArrayList<>();

		CalculoDoRateio.Rateio ordinaria = Regras.aplicar(
				() -> rateios.get(regras.getTipoRateio()).calcular(pedido.valorOrdinariaTotal(), unidades));
		avisos.addAll(ordinaria.avisos());

		List<TaxaExtra> extras = pedido.taxasExtras() == null ? List.of() : pedido.taxasExtras();
		List<Map<UUID, BigDecimal>> valoresDasExtras = new ArrayList<>();
		for (TaxaExtra extra : extras) {
			if (extra.rateio() == TipoDeRateio.VALOR_POR_GRUPO) {
				throw RegraDeNegocioException.invalido("Taxa extra é rateada igual ou pela fração ideal");
			}
			CalculoDoRateio.Rateio r = Regras.aplicar(() -> rateios.get(extra.rateio()).calcular(extra.valorTotal(), unidades));
			valoresDasExtras.add(r.valores());
			avisos.addAll(r.avisos().stream().map(a -> extra.descricao() + ": " + a).toList());
		}

		// Fundo de reserva: o percentual sobre o total da ordinária, distribuído na proporção de cada unidade
		// (assim a soma fecha no centavo, sem perder arredondamentos unidade a unidade).
		Map<UUID, BigDecimal> fundo = Map.of();
		if (regras.getFundoReservaPercentual().signum() > 0) {
			BigDecimal totalOrdinaria = ordinaria.valores().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
			BigDecimal totalFundo = Dinheiro.percentual(totalOrdinaria, regras.getFundoReservaPercentual());
			fundo = totalOrdinaria.signum() > 0 && totalFundo.signum() > 0
					? DistribuicaoDeCentavos.distribuir(totalFundo, ordinaria.valores()) : Map.of();
		}

		Map<UUID, List<BeneficioDeMandato>> beneficios = cadastro.beneficiosNoPeriodo(mes.atDay(1), mes.atEndOfMonth())
				.stream().collect(Collectors.groupingBy(BeneficioDeMandato::unidadeId));

		List<Linha> linhas = new ArrayList<>();
		List<String> isentas = new ArrayList<>();
		for (UnidadeCobravel u : unidades) {
			BigDecimal valorOrdinaria = ordinaria.valores().get(u.id());
			List<ItemDeCobranca> itens = new ArrayList<>();
			itens.add(new ItemDeCobranca(TipoDeItem.TAXA_ORDINARIA, "Taxa ordinária", valorOrdinaria));
			if (fundo.containsKey(u.id())) {
				itens.add(new ItemDeCobranca(TipoDeItem.FUNDO_RESERVA, "Fundo de reserva ("
						+ regras.getFundoReservaPercentual().stripTrailingZeros().toPlainString() + "%)", fundo.get(u.id())));
			}
			for (int i = 0; i < extras.size(); i++) {
				itens.add(new ItemDeCobranca(TipoDeItem.TAXA_EXTRA, extras.get(i).descricao(),
						valoresDasExtras.get(i).get(u.id())));
			}
			BigDecimal desconto = desconto(valorOrdinaria, beneficios.getOrDefault(u.id(), List.of()), mes);
			if (desconto.signum() > 0) {
				itens.add(new ItemDeCobranca(TipoDeItem.DESCONTO_MANDATO, "Desconto de mandato", desconto.negate()));
			}
			BigDecimal total = itens.stream().map(ItemDeCobranca::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
			if (total.signum() <= 0) {
				isentas.add(u.descricao());
				continue;
			}
			if (u.responsavel() == null) {
				avisos.add("A unidade " + u.descricao() + " não tem proprietário, inquilino ou morador cadastrado");
			}
			linhas.add(new Linha(u.id(), u.descricao(), u.responsavel() == null ? null : u.responsavel().nome(), itens, total));
		}
		BigDecimal totalGeral = linhas.stream().map(Linha::total).reduce(BigDecimal.ZERO, BigDecimal::add);
		return new ResultadoDaGeracao(null, mes, vencimento, regras.getTipoRateio(), linhas, isentas, totalGeral, avisos);
	}

	/** Soma dos descontos de mandato, proporcional aos dias do mês em que cada mandato valeu; no máximo a taxa ordinária. */
	private static BigDecimal desconto(BigDecimal ordinaria, List<BeneficioDeMandato> beneficios, YearMonth mes) {
		BigDecimal diasDoMes = BigDecimal.valueOf(mes.lengthOfMonth());
		BigDecimal soma = BigDecimal.ZERO;
		for (BeneficioDeMandato b : beneficios) {
			LocalDate de = b.inicio().isAfter(mes.atDay(1)) ? b.inicio() : mes.atDay(1);
			LocalDate ate = b.fim() == null || b.fim().isAfter(mes.atEndOfMonth()) ? mes.atEndOfMonth() : b.fim();
			long dias = ChronoUnit.DAYS.between(de, ate) + 1;
			if (dias > 0) {
				soma = soma.add(ordinaria.multiply(b.percentual()).multiply(BigDecimal.valueOf(dias))
						.divide(Dinheiro.CEM.multiply(diasDoMes), 10, RoundingMode.HALF_UP));
			}
		}
		BigDecimal desconto = Dinheiro.reais(soma);
		return desconto.compareTo(ordinaria) > 0 ? ordinaria : desconto;
	}

}
