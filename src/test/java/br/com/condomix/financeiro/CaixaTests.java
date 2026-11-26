package br.com.condomix.financeiro;

import br.com.condomix.CenarioDeTeste;
import br.com.condomix.TestcontainersConfiguration;
import br.com.condomix.acesso.Perfil;
import br.com.condomix.compartilhado.FormatoBrasileiro;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Caixa: contas, plano de contas, lançamentos, transferências, pagamentos de cobranças e balancete. */
@Import({ TestcontainersConfiguration.class, CenarioDeTeste.class })
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CaixaTests {

	@Autowired MockMvc mvc;
	@Autowired CenarioDeTeste cenario;

	UUID condominio;
	String sindico;
	YearMonth mes;
	LocalDate hoje;

	@BeforeEach
	void prepararCondominio() throws Exception {
		condominio = cenario.condominio("Residencial");
		sindico = cenario.token(cenario.comPapel(condominio, Perfil.SINDICO));
		hoje = cenario.hoje();
		mes = YearMonth.from(hoje);
	}

	@Test
	void planoDeContasPadraoEContas() throws Exception {
		mvc.perform(get("/api/financeiro/categorias").header("Authorization", sindico))
				.andExpect(jsonPath("$[*].nome", hasItems("Taxa condominial", "Fundo de reserva", "Energia elétrica")));
		enviar(post("/api/financeiro/categorias"), sindico, """
				{"nome": "Jardinagem", "natureza": "DESPESA"}""").andExpect(status().isCreated());
		enviar(post("/api/financeiro/categorias"), sindico, """
				{"nome": "Jardinagem", "natureza": "DESPESA"}""").andExpect(status().isConflict());
		String energia = categoria("Energia elétrica");
		enviar(post("/api/financeiro/categorias/{id}/desativacao", energia), sindico, "{}").andExpect(status().isBadRequest());

		conta("Conta corrente", 1000);
		conta("Fundo de reserva", 0);
		mvc.perform(get("/api/financeiro/contas").header("Authorization", sindico))
				.andExpect(jsonPath("$[?(@.nome == 'Conta corrente')].recebimentoPadrao", hasItem(true)))
				.andExpect(jsonPath("$[?(@.nome == 'Fundo de reserva')].recebimentoPadrao", hasItem(false)));
	}

	@Test
	void despesaTransferenciaEBalancete() throws Exception {
		String corrente = conta("Conta corrente", 1000);
		String fundo = conta("Fundo de reserva", 0);

		lancar("DESPESA", corrente, categoria("Energia elétrica"), hoje, 300, "Conta de luz").andExpect(status().isCreated());
		lancar("DESPESA", corrente, categoria("Taxa condominial"), hoje, 10, "Errada").andExpect(status().isBadRequest());
		lancar("DESPESA", corrente, categoria("Energia elétrica"), mes.atDay(1).minusDays(1), 10, "Antes")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("saldo inicial")));
		// O caixa aceita até 12 meses antes da adesão (o condomínio foi cadastrado hoje, no teste), a partir do dia 1º.
		LocalDate inicioDoHistorico = mes.minusMonths(12).atDay(1);
		lancar("DESPESA", corrente, categoria("Energia elétrica"), inicioDoHistorico.minusDays(1), 10, "Antigo demais")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("A data do lançamento não pode ser antes de "
						+ FormatoBrasileiro.data(inicioDoHistorico) + ": o Condomix aceita até 12 meses antes da adesão do condomínio ("
						+ FormatoBrasileiro.data(hoje) + ")."));
		enviar(post("/api/financeiro/transferencias"), sindico, """
				{"contaOrigemId": "%s", "contaDestinoId": "%s", "valor": 1, "data": "%s"}"""
				.formatted(corrente, fundo, inicioDoHistorico.minusDays(1)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("A data da transferência não pode ser antes de")));
		enviar(post("/api/financeiro/contas"), sindico, """
				{"nome": "Poupança antiga", "tipo": "POUPANCA", "saldoInicial": 10, "dataSaldoInicial": "%s"}"""
				.formatted(inicioDoHistorico.minusDays(1)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("A data do saldo inicial não pode ser antes de")));
		enviar(post("/api/financeiro/contas"), sindico, """
				{"nome": "Poupança do ano passado", "tipo": "POUPANCA", "saldoInicial": 0, "dataSaldoInicial": "%s"}"""
				.formatted(inicioDoHistorico)).andExpect(status().isCreated());
		mvc.perform(get("/api/condominio/adesao").header("Authorization", sindico))
				.andExpect(jsonPath("$.data").value(hoje.toString()))
				.andExpect(jsonPath("$.inicioDoHistorico").value(inicioDoHistorico.toString()));
		String transferencia = JsonPath.read(enviar(post("/api/financeiro/transferencias"), sindico, """
				{"contaOrigemId": "%s", "contaDestinoId": "%s", "valor": 200}""".formatted(corrente, fundo))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$[0].id");

		saldo("Conta corrente", 500.00);
		saldo("Fundo de reserva", 200.00);
		mvc.perform(get("/api/financeiro/balancete").param("competencia", mes.toString()).header("Authorization", sindico))
				.andExpect(jsonPath("$.totalDespesas").value(300.00))
				.andExpect(jsonPath("$.totalReceitas").value(0))
				.andExpect(jsonPath("$.resultado").value(-300.00))
				.andExpect(jsonPath("$.saldoTotalFinal").value(700.00))  // a transferência não muda o total
				.andExpect(jsonPath("$.despesas[0].categoria").value("Energia elétrica"));

		enviar(post("/api/financeiro/lancamentos/{id}/cancelamento", transferencia), sindico, """
				{"motivo": "Valor errado"}""").andExpect(status().isNoContent());
		saldo("Conta corrente", 700.00);
		saldo("Fundo de reserva", 0.00);
	}

	@Test
	void pagamentoDeCobrancaEntraNoCaixaPorCategoriaEEstornoSai() throws Exception {
		String unidade = unidade("101");
		enviar(put("/api/financeiro/configuracao"), sindico, """
				{"tipoRateio": "IGUALITARIO", "diaVencimento": 10, "multaPercentual": 2, "jurosMensalPercentual": 1,
				 "fundoReservaPercentual": 10}""").andExpect(status().isOk());
		enviar(post("/api/financeiro/geracoes"), sindico, """
				{"competencia": "%s", "valorOrdinariaTotal": 1000, "vencimento": "%s"}"""
				.formatted(mes, hoje.minusDays(10))).andExpect(status().isCreated());
		String cobranca = JsonPath.read(mvc.perform(get("/api/cobrancas").param("unidadeId", unidade)
				.header("Authorization", sindico)).andReturn().getResponse().getContentAsString(), "$.itens[0].id");

		// 1.100,00 com 10 dias de atraso: multa 22,00 + juros 3,67.
		enviar(post("/api/cobrancas/{id}/pagamento", cobranca), sindico, """
				{"forma": "PIX"}""").andExpect(jsonPath("$.valorPago").value(1125.67));

		mvc.perform(get("/api/financeiro/contas").header("Authorization", sindico))
				.andExpect(jsonPath("$[0].nome").value("Conta principal"))      // criada sozinha
				.andExpect(jsonPath("$[0].saldoAtual").value(1125.67));
		mvc.perform(get("/api/financeiro/balancete").param("competencia", mes.toString()).header("Authorization", sindico))
				.andExpect(jsonPath("$.totalReceitas").value(1125.67))
				.andExpect(jsonPath("$.receitas[?(@.categoria == 'Taxa condominial')].total", hasItem(1000.00)))
				.andExpect(jsonPath("$.receitas[?(@.categoria == 'Fundo de reserva')].total", hasItem(100.00)))
				.andExpect(jsonPath("$.receitas[?(@.categoria == 'Multas e juros por atraso')].total", hasItem(25.67)));

		String lancamento = JsonPath.read(mvc.perform(get("/api/financeiro/lancamentos").param("de", mes.atDay(1).toString())
				.param("ate", mes.atEndOfMonth().toString()).header("Authorization", sindico))
				.andReturn().getResponse().getContentAsString(), "$[0].id");
		enviar(post("/api/financeiro/lancamentos/{id}/cancelamento", lancamento), sindico, """
				{"motivo": "teste"}""").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("estorne o pagamento")));

		enviar(post("/api/cobrancas/{id}/estorno", cobranca), sindico, "{}").andExpect(status().isOk());
		mvc.perform(get("/api/financeiro/balancete").param("competencia", mes.toString()).header("Authorization", sindico))
				.andExpect(jsonPath("$.totalReceitas").value(0))
				.andExpect(jsonPath("$.inadimplencia").value(1100.00));
	}

	@Test
	void pagamentoComEncargosPerdoadosReduzAMaiorParcela() throws Exception {
		String unidade = unidade("102");
		String cobranca = JsonPath.read(enviar(post("/api/cobrancas"), sindico, """
				{"unidadeId": "%s", "tipo": "RESERVA_DE_ESPACO", "descricao": "Salão de festas", "valor": 300,
				 "vencimento": "%s"}""".formatted(unidade, hoje.minusDays(30))).andReturn().getResponse().getContentAsString(), "$.id");
		enviar(post("/api/cobrancas/{id}/pagamento", cobranca), sindico, """
				{"forma": "DINHEIRO", "valorPago": 280, "observacao": "Desconto combinado"}""").andExpect(status().isOk());

		mvc.perform(get("/api/financeiro/balancete").param("competencia", mes.toString()).header("Authorization", sindico))
				.andExpect(jsonPath("$.receitas", hasSize(1)))
				.andExpect(jsonPath("$.receitas[0].categoria").value("Aluguel de espaços"))
				.andExpect(jsonPath("$.receitas[0].total").value(280.00));
	}

	@Test
	void permissoesEIsolamento() throws Exception {
		String corrente = conta("Conta corrente", 500);
		String morador = cenario.token(cenario.comPapel(condominio, Perfil.MORADOR));
		String conselheiro = cenario.token(cenario.comPapel(condominio, Perfil.CONSELHEIRO));
		UUID outro = cenario.condominio("Outro");
		String sindicoDoOutro = cenario.token(cenario.comPapel(outro, Perfil.SINDICO));

		mvc.perform(get("/api/financeiro/balancete").param("competencia", mes.toString()).header("Authorization", morador))
				.andExpect(status().isOk()).andExpect(jsonPath("$.saldoTotalFinal").value(500.00));
		mvc.perform(get("/api/financeiro/lancamentos").param("de", hoje.toString()).param("ate", hoje.toString())
				.header("Authorization", morador)).andExpect(status().isForbidden());
		mvc.perform(get("/api/financeiro/contas").header("Authorization", conselheiro)).andExpect(status().isOk());
		lancar("DESPESA", corrente, categoria("Limpeza"), hoje, 50, "Produtos", conselheiro).andExpect(status().isForbidden());

		mvc.perform(get("/api/financeiro/contas").header("Authorization", sindicoDoOutro)).andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(get("/api/financeiro/balancete").param("competencia", mes.toString()).header("Authorization", sindicoDoOutro))
				.andExpect(jsonPath("$.saldoTotalFinal").value(0));
		String limpezaDoOutro = JsonPath.<List<String>>read(mvc.perform(get("/api/financeiro/categorias")
				.header("Authorization", sindicoDoOutro)).andReturn().getResponse().getContentAsString(),
				"$[?(@.nome == 'Limpeza')].id").getFirst();
		lancar("DESPESA", corrente, limpezaDoOutro, hoje, 50, "Invasão", sindicoDoOutro).andExpect(status().isNotFound());
	}

	// ----- apoio -----

	private String conta(String nome, int saldo) throws Exception {
		return JsonPath.read(enviar(post("/api/financeiro/contas"), sindico, """
				{"nome": "%s", "tipo": "CONTA_CORRENTE", "saldoInicial": %d, "dataSaldoInicial": "%s"}"""
				.formatted(nome, saldo, mes.atDay(1))).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.id");
	}

	private String categoria(String nome) throws Exception {
		return JsonPath.<List<String>>read(mvc.perform(get("/api/financeiro/categorias").header("Authorization", sindico))
				.andReturn().getResponse().getContentAsString(), "$[?(@.nome == '%s')].id".formatted(nome)).getFirst();
	}

	private String unidade(String identificacao) throws Exception {
		return JsonPath.read(enviar(post("/api/unidades"), sindico, """
				{"identificacao": "%s", "tipo": "APARTAMENTO"}""".formatted(identificacao))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	private void saldo(String conta, double esperado) throws Exception {
		mvc.perform(get("/api/financeiro/contas").header("Authorization", sindico))
				.andExpect(jsonPath("$[?(@.nome == '%s')].saldoAtual".formatted(conta), hasItem(esperado)));
	}

	private ResultActions lancar(String tipo, String conta, String categoria, LocalDate data, int valor, String descricao)
			throws Exception {
		return lancar(tipo, conta, categoria, data, valor, descricao, sindico);
	}

	private ResultActions lancar(String tipo, String conta, String categoria, LocalDate data, int valor, String descricao,
			String token) throws Exception {
		return enviar(post("/api/financeiro/lancamentos"), token, """
				{"tipo": "%s", "contaId": "%s", "categoriaId": "%s", "data": "%s", "valor": %d, "descricao": "%s"}"""
				.formatted(tipo, conta, categoria, data, valor, descricao));
	}

	private ResultActions enviar(MockHttpServletRequestBuilder pedido, String token, String corpo) throws Exception {
		return mvc.perform(pedido.contentType(MediaType.APPLICATION_JSON).content(corpo).header("Authorization", token));
	}

}
