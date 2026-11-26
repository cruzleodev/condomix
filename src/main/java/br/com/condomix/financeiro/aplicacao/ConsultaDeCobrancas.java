package br.com.condomix.financeiro.aplicacao;

import br.com.condomix.acesso.CadastroParaCobranca;
import br.com.condomix.acesso.CadastroParaCobranca.UnidadeCobravel;
import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.financeiro.dominio.Boleto;
import br.com.condomix.financeiro.dominio.Cobranca;
import br.com.condomix.financeiro.dominio.Encargos;
import br.com.condomix.financeiro.dominio.StatusCobranca;
import br.com.condomix.financeiro.persistencia.BoletoRepository;
import br.com.condomix.financeiro.persistencia.CobrancaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Caso de uso: consultar cobranças, a inadimplência e os débitos do próprio morador. */
@Service
@Transactional(readOnly = true)
public class ConsultaDeCobrancas {

	/** Quantos meses de cobranças pagas o morador vê em "meus débitos". */
	static final int MESES_DE_HISTORICO = 12;

	private final CobrancaRepository cobrancas;

	private final CadastroParaCobranca cadastro;

	private final BoletoRepository boletos;

	private final Clock relogio;

	public ConsultaDeCobrancas(CobrancaRepository cobrancas, CadastroParaCobranca cadastro, BoletoRepository boletos,
			Clock relogio) {
		this.cobrancas = cobrancas;
		this.cadastro = cadastro;
		this.boletos = boletos;
		this.relogio = relogio;
	}

	/** Uma cobrança com o valor atualizado para hoje, a descrição da unidade e o boleto (vazio se não tem). */
	public record CobrancaHoje(Cobranca cobranca, String unidade, boolean vencida, Encargos encargos, Boleto boleto) {
	}

	/**
	 * @param diasMaiorAtraso há quantos dias está vencida a cobrança mais antiga
	 * @param valores        soma das cobranças vencidas: principal, multa, juros e total atualizado
	 */
	public record Inadimplente(UUID unidadeId, String unidade, String responsavel, int cobrancasVencidas,
			long diasMaiorAtraso, Encargos valores) {
	}

	public Page<CobrancaHoje> listar(YearMonth competencia, StatusCobranca status, UUID unidadeId, int pagina, int tamanho) {
		var pedido = PageRequest.of(Math.max(pagina, 0), Math.clamp(tamanho, 1, 1000),
				Sort.by(Sort.Order.desc("vencimento"), Sort.Order.asc("unidadeId")));
		Map<UUID, String> nomes = nomesDasUnidades();
		LocalDate hoje = hoje();
		Page<Cobranca> encontradas = cobrancas.findAll(
				CobrancaRepository.filtros(competencia == null ? null : competencia.atDay(1), status, unidadeId), pedido);
		Map<UUID, Boleto> doPedido = boletosDe(encontradas.getContent());
		return encontradas.map(c -> hoje(c, nomes, hoje, doPedido));
	}

	public CobrancaHoje obter(UUID id) {
		Cobranca c = cobrancas.findById(id).orElseThrow(() -> new NaoEncontradoException("Cobrança não encontrada"));
		return hoje(c, nomesDasUnidades(), hoje(), boletosDe(List.of(c)));
	}

	/** Unidades com cobranças vencidas, das que devem há mais tempo para as mais recentes. */
	public List<Inadimplente> inadimplencia() {
		LocalDate hoje = hoje();
		Map<UUID, UnidadeCobravel> unidades = cadastro.unidadesAtivas(hoje).stream()
				.collect(Collectors.toMap(UnidadeCobravel::id, Function.identity()));
		return cobrancas.vencidasEm(hoje).stream()
				.collect(Collectors.groupingBy(Cobranca::getUnidadeId))
				.entrySet().stream()
				.map(e -> {
					List<Encargos> valores = e.getValue().stream().map(c -> c.encargosEm(hoje)).toList();
					var u = unidades.get(e.getKey());
					return new Inadimplente(e.getKey(), u == null ? "(unidade desativada)" : u.descricao(),
							u == null || u.responsavel() == null ? null : u.responsavel().nome(), e.getValue().size(),
							valores.stream().mapToLong(Encargos::diasDeAtraso).max().orElse(0), somar(valores));
				})
				.sorted(Comparator.comparingLong(Inadimplente::diasMaiorAtraso).reversed())
				.toList();
	}

	/** "Meus débitos": cobranças em aberto e as dos últimos 12 meses das unidades de que o usuário é proprietário ou inquilino. */
	public List<CobrancaHoje> doUsuario(UUID usuarioId) {
		LocalDate hoje = hoje();
		Set<UUID> unidades = cadastro.unidadesDoPagador(usuarioId, hoje);
		if (unidades.isEmpty()) {
			return List.of();
		}
		Map<UUID, String> nomes = nomesDasUnidades();
		List<Cobranca> lista = cobrancas.daUnidades(unidades, hoje.minusMonths(MESES_DE_HISTORICO));
		Map<UUID, Boleto> doUsuario = boletosDe(lista);
		return lista.stream().map(c -> hoje(c, nomes, hoje, doUsuario)).toList();
	}

	private static CobrancaHoje hoje(Cobranca c, Map<UUID, String> nomes, LocalDate hoje, Map<UUID, Boleto> boletos) {
		return new CobrancaHoje(c, nomes.getOrDefault(c.getUnidadeId(), "(unidade desativada)"), c.vencidaEm(hoje),
				c.getStatus() == StatusCobranca.CANCELADA ? null : c.encargosEm(hoje), boletos.get(c.getId()));
	}

	/** Os boletos vigentes das cobranças, numa consulta só. */
	private Map<UUID, Boleto> boletosDe(List<Cobranca> lista) {
		if (lista.isEmpty()) {
			return Map.of();
		}
		return boletos.vigentes(lista.stream().map(Cobranca::getId).toList()).stream()
				.collect(Collectors.toMap(Boleto::getCobrancaId, Function.identity(), (a, b) -> a));
	}

	private Map<UUID, String> nomesDasUnidades() {
		return cadastro.unidadesAtivas(hoje()).stream()
				.collect(Collectors.toMap(UnidadeCobravel::id, UnidadeCobravel::descricao));
	}

	private static Encargos somar(List<Encargos> lista) {
		BigDecimal principal = BigDecimal.ZERO, multa = BigDecimal.ZERO, juros = BigDecimal.ZERO;
		for (Encargos e : lista) {
			principal = principal.add(e.principal());
			multa = multa.add(e.multa());
			juros = juros.add(e.juros());
		}
		return new Encargos(lista.stream().mapToLong(Encargos::diasDeAtraso).max().orElse(0), principal, multa, juros,
				principal.add(multa).add(juros));
	}

	private LocalDate hoje() {
		return LocalDate.now(relogio);
	}

}
