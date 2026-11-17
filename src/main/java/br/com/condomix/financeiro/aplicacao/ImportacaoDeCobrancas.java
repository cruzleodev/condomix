package br.com.condomix.financeiro.aplicacao;

import br.com.condomix.acesso.CadastroParaCobranca;
import br.com.condomix.acesso.CadastroParaCobranca.UnidadeCobravel;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.financeiro.dominio.Cobranca;
import br.com.condomix.financeiro.dominio.ConfiguracaoDaCobranca;
import br.com.condomix.financeiro.dominio.FormaDePagamento;
import br.com.condomix.financeiro.dominio.ItemDeCobranca;
import br.com.condomix.financeiro.dominio.OrigemCobranca;
import br.com.condomix.financeiro.dominio.TipoDeItem;
import br.com.condomix.financeiro.persistencia.CobrancaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Caso de uso: trazer as cobranças do sistema da administradora (planilha CSV). Tudo ou nada: se houver
 * erro em alguma linha, nada é importado e a resposta lista os erros.
 *
 * <p>Colunas (cabeçalho obrigatório, em qualquer ordem; separador ";" ou ","):
 * {@code unidade; competencia; vencimento; valor; situacao; data_pagamento; valor_pago; descricao}.
 * Aceita datas como 10/12/2026 ou 2026-12-10, competência como 12/2026 ou 2026-12, valores como 1.234,56
 * ou 1234.56, situação ABERTA/PAGA (ou "em aberto"/"pago"). Planilhas do Excel em UTF-8 ou Windows-1252.
 */
@Service
@Transactional
public class ImportacaoDeCobrancas {

	static final int MAXIMO_DE_LINHAS = 10_000;

	private static final List<String> OBRIGATORIAS = List.of("unidade", "competencia", "vencimento", "valor", "situacao");

	private final CadastroParaCobranca cadastro;

	private final ConfiguracaoDeCobrancas configuracoes;

	private final CobrancaRepository cobrancas;

	private final Clock relogio;

	public ImportacaoDeCobrancas(CadastroParaCobranca cadastro, ConfiguracaoDeCobrancas configuracoes,
			CobrancaRepository cobrancas, Clock relogio) {
		this.cadastro = cadastro;
		this.configuracoes = configuracoes;
		this.cobrancas = cobrancas;
		this.relogio = relogio;
	}

	public record ResultadoDaImportacao(int importadas, int pagas, int abertas) {
	}

	private record LinhaValida(UnidadeCobravel unidade, YearMonth competencia, LocalDate vencimento, BigDecimal valor,
			boolean paga, LocalDate pagoEm, BigDecimal valorPago, String descricao) {
	}

	public ResultadoDaImportacao importar(byte[] arquivo, UUID importadoPor) {
		List<String> linhas = texto(arquivo).lines().filter(l -> !l.isBlank()).toList();
		if (linhas.size() < 2) {
			throw RegraDeNegocioException.invalido("A planilha está vazia: precisa do cabeçalho e de pelo menos uma linha");
		}
		if (linhas.size() - 1 > MAXIMO_DE_LINHAS) {
			throw RegraDeNegocioException.invalido("No máximo " + MAXIMO_DE_LINHAS + " linhas por importação");
		}
		String separador = linhas.getFirst().contains(";") ? ";" : ",";
		Map<String, Integer> colunas = colunas(linhas.getFirst(), separador);
		Map<String, UnidadeCobravel> unidades = cadastro.unidadesAtivas(LocalDate.now(relogio)).stream()
				.collect(Collectors.toMap(u -> chave(u.descricao()), Function.identity(), (a, b) -> a));

		List<String> erros = new ArrayList<>();
		List<LinhaValida> validas = new ArrayList<>();
		Set<String> vistas = new HashSet<>();
		for (int i = 1; i < linhas.size(); i++) {
			int numero = i + 1;
			try {
				LinhaValida l = ler(linhas.get(i).split(separador, -1), colunas, unidades);
				if (!vistas.add(l.unidade().id() + "|" + l.competencia())) {
					throw new IllegalArgumentException("a unidade " + l.unidade().descricao() + " aparece duas vezes em " + l.competencia());
				}
				if (cobrancas.existeNaCompetencia(l.unidade().id(), l.competencia().atDay(1))) {
					throw new IllegalArgumentException("a unidade " + l.unidade().descricao() + " já tem cobrança em " + l.competencia());
				}
				validas.add(l);
			}
			catch (IllegalArgumentException | DateTimeParseException | ArrayIndexOutOfBoundsException e) {
				erros.add("Linha " + numero + ": " + e.getMessage());
			}
		}
		if (!erros.isEmpty()) {
			throw RegraDeNegocioException.invalido("Nada foi importado. Corrija a planilha: "
					+ String.join("; ", erros.subList(0, Math.min(erros.size(), 20)))
					+ (erros.size() > 20 ? " (e mais " + (erros.size() - 20) + " erros)" : ""));
		}

		ConfiguracaoDaCobranca regras = configuracoes.atual();
		UUID tenant = TenantAtual.obrigatorio();
		int pagas = 0;
		for (LinhaValida l : validas) {
			var item = new ItemDeCobranca(TipoDeItem.TAXA_ORDINARIA, l.descricao(), l.valor());
			Cobranca c = new Cobranca(new Cobranca.Emissao(tenant, l.unidade().id(),
					l.unidade().responsavel() == null ? null : l.unidade().responsavel().pessoaId(), OrigemCobranca.IMPORTACAO,
					null, l.competencia(), l.descricao(), l.vencimento(), List.of(item), regras, importadoPor));
			if (l.paga()) {
				c.registrarPagamento(l.pagoEm(), l.valorPago() != null ? l.valorPago() : l.valor(), FormaDePagamento.OUTRO,
						"Importado do sistema da administradora", importadoPor);
				pagas++;
			}
			cobrancas.save(c);
		}
		return new ResultadoDaImportacao(validas.size(), pagas, validas.size() - pagas);
	}

	private static LinhaValida ler(String[] campos, Map<String, Integer> colunas, Map<String, UnidadeCobravel> unidades) {
		String nomeUnidade = campo(campos, colunas, "unidade");
		UnidadeCobravel unidade = unidades.get(chave(nomeUnidade));
		if (unidade == null) {
			throw new IllegalArgumentException("unidade \"" + nomeUnidade + "\" não encontrada (use como no cadastro, ex.: A-101)");
		}
		YearMonth competencia = competencia(campo(campos, colunas, "competencia"));
		LocalDate vencimento = data(campo(campos, colunas, "vencimento"));
		BigDecimal valor = valor(campo(campos, colunas, "valor"));
		if (valor.signum() <= 0) {
			throw new IllegalArgumentException("valor deve ser maior que zero");
		}
		String situacao = campo(campos, colunas, "situacao").toLowerCase(Locale.ROOT);
		boolean paga = situacao.startsWith("pag");
		if (!paga && !situacao.contains("aberta") && !situacao.contains("aberto")) {
			throw new IllegalArgumentException("situação \"" + situacao + "\" inválida (use ABERTA ou PAGA)");
		}
		LocalDate pagoEm = null;
		BigDecimal valorPago = null;
		if (paga) {
			String dataPagamento = opcional(campos, colunas, "data_pagamento");
			if (dataPagamento == null) {
				throw new IllegalArgumentException("cobrança paga precisa da data_pagamento");
			}
			pagoEm = data(dataPagamento);
			String pago = opcional(campos, colunas, "valor_pago");
			valorPago = pago == null ? null : valor(pago);
		}
		String descricao = opcional(campos, colunas, "descricao");
		return new LinhaValida(unidade, competencia, vencimento, valor, paga, pagoEm, valorPago,
				descricao != null ? descricao : "Taxa condominial " + competencia + " (importada)");
	}

	private static Map<String, Integer> colunas(String cabecalho, String separador) {
		List<String> nomes = Arrays.stream(cabecalho.split(separador, -1))
				.map(n -> Normalizer.normalize(n.strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
						.replaceAll("\\p{M}", "").replace(" ", "_"))
				.toList();
		List<String> faltando = OBRIGATORIAS.stream().filter(o -> !nomes.contains(o)).toList();
		if (!faltando.isEmpty()) {
			throw RegraDeNegocioException.invalido("Faltam colunas no cabeçalho: " + String.join(", ", faltando)
					+ ". Esperado: unidade;competencia;vencimento;valor;situacao;data_pagamento;valor_pago;descricao");
		}
		Map<String, Integer> indices = new HashMap<>();
		for (int i = 0; i < nomes.size(); i++) {
			indices.put(nomes.get(i), i);
		}
		return indices;
	}

	private static String campo(String[] campos, Map<String, Integer> colunas, String nome) {
		String valor = opcional(campos, colunas, nome);
		if (valor == null) {
			throw new IllegalArgumentException(nome + " vazio");
		}
		return valor;
	}

	private static String opcional(String[] campos, Map<String, Integer> colunas, String nome) {
		Integer indice = colunas.get(nome);
		if (indice == null || indice >= campos.length) {
			return null;
		}
		String valor = campos[indice].strip().replaceAll("^\"|\"$", "");
		return valor.isEmpty() ? null : valor;
	}

	/** "A-101", "a 101" e "A101" são a mesma unidade. */
	private static String chave(String descricao) {
		return descricao.toLowerCase(Locale.ROOT).replaceAll("[\\s-]", "");
	}

	private static LocalDate data(String texto) {
		return texto.contains("/") ? LocalDate.parse(texto, DateTimeFormatter.ofPattern("dd/MM/yyyy")) : LocalDate.parse(texto);
	}

	private static YearMonth competencia(String texto) {
		return texto.contains("/") ? YearMonth.parse(texto, DateTimeFormatter.ofPattern("MM/yyyy")) : YearMonth.parse(texto);
	}

	/** Aceita "1.234,56", "1234,56" e "1234.56". */
	private static BigDecimal valor(String texto) {
		String limpo = texto.replace("R$", "").strip();
		if (limpo.contains(",")) {
			limpo = limpo.replace(".", "").replace(",", ".");
		}
		try {
			return new BigDecimal(limpo);
		}
		catch (NumberFormatException e) {
			throw new IllegalArgumentException("valor \"" + texto + "\" inválido");
		}
	}

	/** UTF-8 (com ou sem BOM); se não for, Windows-1252 (padrão do Excel em português). */
	private static String texto(byte[] arquivo) {
		try {
			String utf8 = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(arquivo)).toString();
			return utf8.startsWith("﻿") ? utf8.substring(1) : utf8;
		}
		catch (CharacterCodingException e) {
			return new String(arquivo, Charset.forName("windows-1252"));
		}
	}

}
