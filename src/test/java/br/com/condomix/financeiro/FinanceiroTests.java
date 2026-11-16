package br.com.condomix.financeiro;

import br.com.condomix.CenarioDeTeste;
import br.com.condomix.TestcontainersConfiguration;
import br.com.condomix.acesso.Perfil;
import br.com.condomix.acesso.Usuario;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.Charset;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Financeiro de ponta a ponta: rateios, geração, desconto de mandato, baixa, inadimplência, meus débitos e importação. */
@Import({ TestcontainersConfiguration.class, CenarioDeTeste.class })
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FinanceiroTests {

	@Autowired MockMvc mvc;
	@Autowired CenarioDeTeste cenario;

	UUID condominio;
	String sindico;
	YearMonth proximoMes;

	@BeforeEach
	void prepararCondominio() throws Exception {
		condominio = cenario.condominio("Residencial");
		sindico = cenario.token(cenario.comPapel(condominio, Perfil.SINDICO));
		proximoMes = YearMonth.from(cenario.hoje()).plusMonths(1);
	}

	@Test
	void rateioIgualitarioComFundoDeReservaFechaOCentavo() throws Exception {
		unidades("A", "101", "102", "103");
		configurar("IGUALITARIO", 10);

		gerar("""
				{"competencia": "%s", "valorOrdinariaTotal": 1000}""".formatted(proximoMes), true)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.geracaoId").doesNotExist())  // simulação não grava
				.andExpect(jsonPath("$.cobrancas", hasSize(3)))
				.andExpect(jsonPath("$.cobrancas[*].itens[0].valor", containsInAnyOrder(333.34, 333.33, 333.33)))
				.andExpect(jsonPath("$.cobrancas[0].itens[1].tipo").value("FUNDO_RESERVA"))
				.andExpect(jsonPath("$.totalGeral").value(1100.00));
		mvc.perform(get("/api/cobrancas").header("Authorization", sindico)).andExpect(jsonPath("$.total").value(0));
	}

	@Test
	void rateioPorFracaoIdealGeraUmaVezPorMesEPodeSerCancelado() throws Exception {
		unidadeComFracao("C-1", 0.5);
		unidadeComFracao("C-2", 0.3);
		unidadeComFracao("C-3", 0.2);
		configurar("FRACAO_IDEAL", 0);
		String corpo = """
				{"competencia": "%s", "valorOrdinariaTotal": 2000,
				 "taxasExtras": [{"descricao": "Pintura", "valorTotal": 900, "rateio": "IGUALITARIO"}]}""".formatted(proximoMes);

		String geracao = JsonPath.read(gerar(corpo, false).andExpect(status().isCreated())
				.andExpect(jsonPath("$.cobrancas[*].total", containsInAnyOrder(1300.00, 900.00, 700.00)))
				.andReturn().getResponse().getContentAsString(), "$.geracaoId");
		gerar(corpo, false).andExpect(status().isConflict());
		mvc.perform(get("/api/cobrancas").param("competencia", proximoMes.toString()).header("Authorization", sindico))
				.andExpect(jsonPath("$.total").value(3));

		enviar(post("/api/financeiro/geracoes/{id}/cancelamento", geracao), sindico, "{}").andExpect(status().isNoContent());
		mvc.perform(get("/api/cobrancas").param("status", "ABERTA").header("Authorization", sindico))
				.andExpect(jsonPath("$.total").value(0));
		gerar(corpo, false).andExpect(status().isCreated());
	}

	@Test
	void rateioPorFracaoIdealExigeAFracaoDeTodas() throws Exception {
		unidadeComFracao("C-1", 0.5);
		unidades(null, "C-2");
		configurar("FRACAO_IDEAL", 0);
		gerar("""
				{"competencia": "%s", "valorOrdinariaTotal": 2000}""".formatted(proximoMes), true)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("C-2")));
	}

	@Test
	void valorPorGrupoComCobertura() throws Exception {
		List<String> ids = unidades("A", "101", "102", "1001");
		configurar("VALOR_POR_GRUPO", 0);
		String tipo = idDe(enviar(post("/api/financeiro/grupos"), sindico, """
				{"nome": "Apartamento tipo", "valorMensal": 500}"""));
		String cobertura = idDe(enviar(post("/api/financeiro/grupos"), sindico, """
				{"nome": "Cobertura", "valorMensal": 850}"""));
		enviar(put("/api/financeiro/grupos/{id}/unidades", tipo), sindico, """
				{"unidades": ["%s", "%s"]}""".formatted(ids.get(0), ids.get(1))).andExpect(status().isOk());

		gerar("""
				{"competencia": "%s"}""".formatted(proximoMes), true)
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("A-1001")));

		enviar(put("/api/financeiro/grupos/{id}/unidades", cobertura), sindico, """
				{"unidades": ["%s"]}""".formatted(ids.get(2))).andExpect(status().isOk());
		gerar("""
				{"competencia": "%s"}""".formatted(proximoMes), true)
				.andExpect(jsonPath("$.cobrancas[*].total", containsInAnyOrder(500.00, 500.00, 850.00)));
	}

	@Test
	void descontoDoSindicoProporcionalAosDiasDoMandato() throws Exception {
		List<String> ids = unidades("A", "101", "102");
		configurar("IGUALITARIO", 0);
		String sindicoMorador = cenario.pessoa(condominio, "Síndico Morador", null).getId().toString();
		// O mandato com isenção de 100% começa no dia 16 do mês cobrado: desconto de (dias restantes / dias do mês).
		var posse = proximoMes.atDay(16);
		enviar(post("/api/mandatos"), sindico, """
				{"pessoaId": "%s", "cargo": "SUBSINDICO", "inicio": "%s", "fimPrevisto": "%s",
				 "descontoPercentual": 100, "unidadeBeneficiadaId": "%s"}"""
				.formatted(sindicoMorador, posse, posse.plusYears(1).minusDays(1), ids.get(0)))
				.andExpect(status().isCreated());
		int dias = proximoMes.lengthOfMonth() - 15;
		double desconto = Math.round(600.0 * dias / proximoMes.lengthOfMonth() * 100) / 100.0;

		gerar("""
				{"competencia": "%s", "valorOrdinariaTotal": 1200}""".formatted(proximoMes), true)
				.andExpect(jsonPath("$.cobrancas[?(@.unidade == 'A-101')].itens[1].tipo").value("DESCONTO_MANDATO"))
				.andExpect(jsonPath("$.cobrancas[?(@.unidade == 'A-101')].itens[1].valor").value(-desconto))
				.andExpect(jsonPath("$.cobrancas[?(@.unidade == 'A-102')].total").value(600.00));
	}

	@Test
	void pagamentoAtrasadoComMultaEJurosInadimplenciaEEstorno() throws Exception {
		String unidade = unidades("A", "101").getFirst();
		String cobranca = idDe(enviar(post("/api/cobrancas"), sindico, """
				{"unidadeId": "%s", "tipo": "MULTA_DISCIPLINAR", "descricao": "Barulho após 22h",
				 "valor": 100, "vencimento": "%s"}""".formatted(unidade, cenario.hoje().minusDays(30))));

		mvc.perform(get("/api/cobrancas/inadimplencia").header("Authorization", sindico))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].unidade").value("A-101"))
				.andExpect(jsonPath("$[0].diasMaiorAtraso").value(30))
				.andExpect(jsonPath("$[0].valores.total").value(103.00));  // 100 + 2% multa + 1% juros (30 dias)

		enviar(post("/api/cobrancas/{id}/pagamento", cobranca), sindico, """
				{"forma": "PIX"}""")
				.andExpect(jsonPath("$.status").value("PAGA"))
				.andExpect(jsonPath("$.valorPago").value(103.00));
		mvc.perform(get("/api/cobrancas/inadimplencia").header("Authorization", sindico)).andExpect(jsonPath("$", hasSize(0)));

		enviar(post("/api/cobrancas/{id}/estorno", cobranca), sindico, "{}").andExpect(jsonPath("$.status").value("ABERTA"));
		enviar(post("/api/cobrancas/{id}/cancelamento", cobranca), sindico, """
				{"motivo": "Multa revogada pelo conselho"}""").andExpect(jsonPath("$.status").value("CANCELADA"));
		enviar(post("/api/cobrancas/{id}/pagamento", cobranca), sindico, """
				{"forma": "PIX"}""").andExpect(status().isBadRequest());
	}

	@Test
	void meusDebitosMostramSoAsUnidadesDoPagador() throws Exception {
		List<String> ids = unidades("A", "101", "102");
		configurar("IGUALITARIO", 0);
		Usuario ana = cenario.usuario("ana");
		cenario.perfil(condominio, ana, Perfil.MORADOR);
		String pessoaAna = cenario.pessoa(condominio, "Ana", ana).getId().toString();
		enviar(post("/api/pessoas/{id}/vinculos", pessoaAna), sindico, """
				{"unidadeId": "%s", "tipo": "PROPRIETARIO"}""".formatted(ids.get(0))).andExpect(status().isCreated());
		gerar("""
				{"competencia": "%s", "valorOrdinariaTotal": 1000}""".formatted(proximoMes), false).andExpect(status().isCreated());

		mvc.perform(get("/api/eu/cobrancas").header("Authorization", cenario.token(ana)))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].unidade").value("A-101"))
				.andExpect(jsonPath("$[0].valorAtualizado").value(500.00));
		mvc.perform(get("/api/eu/cobrancas").header("Authorization", sindico)).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void importacaoDaAdministradoraTudoOuNada() throws Exception {
		unidades("A", "101", "102");
		String planilha = """
				unidade;competência;vencimento;valor;situação;data_pagamento;valor_pago;descricao
				A-101;10/2026;10/10/2026;1.234,56;PAGA;09/10/2026;1.234,56;Taxa outubro
				a 102;10/2026;10/10/2026;980,00;em aberto;;;
				""";
		String comErro = planilha + "Z-999;10/2026;10/10/2026;100,00;ABERTA;;;\n";

		importar(comErro).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("Linha 4: unidade \"Z-999\" não encontrada")));
		mvc.perform(get("/api/cobrancas").header("Authorization", sindico)).andExpect(jsonPath("$.total").value(0));

		importar(planilha).andExpect(status().isCreated())
				.andExpect(jsonPath("$.importadas").value(2))
				.andExpect(jsonPath("$.pagas").value(1));
		importar(planilha).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("já tem cobrança")));
		mvc.perform(get("/api/cobrancas").param("status", "PAGA").header("Authorization", sindico))
				.andExpect(jsonPath("$.itens[0].valorPago").value(1234.56));
	}

	@Test
	void permissoesEIsolamento() throws Exception {
		String unidade = unidades("A", "101").getFirst();
		String cobranca = idDe(enviar(post("/api/cobrancas"), sindico, """
				{"unidadeId": "%s", "tipo": "OUTRO", "descricao": "Chave extra", "valor": 30, "vencimento": "%s"}"""
				.formatted(unidade, cenario.hoje().plusDays(5))));
		String morador = cenario.token(cenario.comPapel(condominio, Perfil.MORADOR));
		String conselheiro = cenario.token(cenario.comPapel(condominio, Perfil.CONSELHEIRO));
		UUID outro = cenario.condominio("Outro");
		String sindicoDoOutro = cenario.token(cenario.comPapel(outro, Perfil.SINDICO));

		gerar("""
				{"competencia": "%s", "valorOrdinariaTotal": 10}""".formatted(proximoMes), false, morador)
				.andExpect(status().isForbidden());
		mvc.perform(get("/api/cobrancas").header("Authorization", morador)).andExpect(status().isForbidden());
		mvc.perform(get("/api/cobrancas").header("Authorization", conselheiro)).andExpect(jsonPath("$.total").value(1));
		mvc.perform(get("/api/cobrancas").header("Authorization", sindicoDoOutro)).andExpect(jsonPath("$.total").value(0));
		mvc.perform(get("/api/cobrancas/{id}", cobranca).header("Authorization", sindicoDoOutro)).andExpect(status().isNotFound());
		enviar(post("/api/cobrancas/{id}/pagamento", cobranca), sindicoDoOutro, """
				{"forma": "PIX"}""").andExpect(status().isNotFound());
	}

	// ----- apoio -----

	private List<String> unidades(String bloco, String... identificacoes) throws Exception {
		String lista = String.join("\", \"", identificacoes);
		String json = enviar(post("/api/unidades/lote"), sindico, """
				{%s"tipo": "APARTAMENTO", "identificacoes": ["%s"]}"""
				.formatted(bloco == null ? "" : "\"bloco\": \"" + bloco + "\", ", lista))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$[*].id");
	}

	private void unidadeComFracao(String identificacao, double fracao) throws Exception {
		enviar(post("/api/unidades"), sindico, """
				{"identificacao": "%s", "tipo": "CASA", "fracaoIdeal": %s}""".formatted(identificacao, fracao))
				.andExpect(status().isCreated());
	}

	private void configurar(String rateio, int fundoReserva) throws Exception {
		enviar(put("/api/financeiro/configuracao"), sindico, """
				{"tipoRateio": "%s", "diaVencimento": 10, "multaPercentual": 2, "jurosMensalPercentual": 1,
				 "fundoReservaPercentual": %d}""".formatted(rateio, fundoReserva)).andExpect(status().isOk());
	}

	private ResultActions gerar(String corpo, boolean simular) throws Exception {
		return gerar(corpo, simular, sindico);
	}

	private ResultActions gerar(String corpo, boolean simular, String token) throws Exception {
		return enviar(post(simular ? "/api/financeiro/geracoes/simulacao" : "/api/financeiro/geracoes"), token, corpo);
	}

	private ResultActions importar(String conteudo) throws Exception {
		return mvc.perform(multipart("/api/cobrancas/importacao")
				.file(new MockMultipartFile("arquivo", "cobrancas.csv", "text/csv", conteudo.getBytes(Charset.forName("windows-1252"))))
				.header("Authorization", sindico));
	}

	private ResultActions enviar(MockHttpServletRequestBuilder pedido, String token, String corpo) throws Exception {
		return mvc.perform(pedido.contentType(MediaType.APPLICATION_JSON).content(corpo).header("Authorization", token));
	}

	private static String idDe(ResultActions resposta) throws Exception {
		return JsonPath.read(resposta.andReturn().getResponse().getContentAsString(), "$.id");
	}

}
