package br.com.condomix.faturamento;

import br.com.condomix.CenarioDeTeste;
import br.com.condomix.TestcontainersConfiguration;
import br.com.condomix.acesso.Perfil;
import br.com.condomix.acesso.Usuario;
import br.com.condomix.faturamento.aplicacao.EmissaoDeFaturas;
import br.com.condomix.faturamento.aplicacao.RecebimentoDeFaturas;
import br.com.condomix.pagamentos.TipoDeGateway;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobrança da assinatura do Condomix (gateway simulado): fim do teste, fatura com boleto e Pix, baixa, suspensão
 * por atraso com entrada só para pagar, perdão da fatura, troca de plano e separação por condomínio.
 * As datas no passado são feitas direto no banco (o relógio dos testes é o de verdade).
 */
@Import({ TestcontainersConfiguration.class, CenarioDeTeste.class })
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FaturamentoTests {

	/** Um CNPJ válido e diferente a cada teste (o CNPJ é único entre os clientes). */
	private String cnpj = cnpjAleatorio();

	@Autowired MockMvc mvc;
	@Autowired CenarioDeTeste cenario;
	@Autowired EmissaoDeFaturas emissao;
	@Autowired RecebimentoDeFaturas recebimento;
	@Autowired JdbcTemplate jdbc;

	UUID condominio;
	Usuario sindico;
	String admin;
	String tokenDoAviso;

	@BeforeEach
	void prepararClienteEmTesteComAContaDoCondomixLigada() throws Exception {
		admin = cenario.token(cenario.administrador());
		enviar(put("/api/plataforma/planos/{codigo}/precos", "COMPLETO"), admin, """
				{"mensal": 199.90, "semestral": 1099.00, "anual": 1999.00}""").andExpect(status().isOk());
		enviar(put("/api/plataforma/planos/{codigo}/precos", "ESSENCIAL"), admin, """
				{"mensal": 99.90, "semestral": 549.00, "anual": 999.00}""").andExpect(status().isOk());
		tokenDoAviso = JsonPath.read(enviar(put("/api/plataforma/recebimento"), admin, """
				{"gateway": "SIMULADO", "ambiente": "SANDBOX", "chave": "chave-do-condomix"}""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.url").value(containsString("/api/avisos/simulado/assinaturas")))
				.andReturn().getResponse().getContentAsString(), "$.token");
		condominio = cenario.condominio("Residencial da Fatura " + UUID.randomUUID());
		sindico = cenario.comPapel(condominio, Perfil.SINDICO);
	}

	@Test
	void clienteNovoTemTrintaDiasDeTesteEAoFimAFaturaSaiComBoletoEPix() throws Exception {
		mvc.perform(get("/api/plataforma/condominios/{id}", condominio).header("Authorization", admin))
				.andExpect(jsonPath("$.assinatura.status").value("TESTE"))
				.andExpect(jsonPath("$.assinatura.fimDoTeste").value(cenario.hoje().plusDays(30).toString()));
		dadosParaAFatura(cnpj);

		encerrarOTesteHoje();

		mvc.perform(get("/api/plataforma/condominios/{id}", condominio).header("Authorization", admin))
				.andExpect(jsonPath("$.assinatura.status").value("ATIVA"))
				.andExpect(jsonPath("$.assinatura.fimDoTeste").doesNotExist());
		mvc.perform(get("/api/plataforma/condominios/{id}/historico", condominio).header("Authorization", admin))
				.andExpect(jsonPath("$[0].tipo").value("FIM_DO_TESTE"))
				.andExpect(jsonPath("$[1].tipo").value("PRAZO_DO_TESTE"));
		mvc.perform(get("/api/plataforma/faturas").param("condominioId", condominio.toString()).header("Authorization", admin))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].situacao").value("A_EMITIR"))
				.andExpect(jsonPath("$[0].valor").value(199.90))
				.andExpect(jsonPath("$[0].plano").value("Completo"))
				.andExpect(jsonPath("$[0].periodoInicio").value(cenario.hoje().toString()))
				.andExpect(jsonPath("$[0].periodoFim").value(cenario.hoje().plusMonths(1).toString()))
				.andExpect(jsonPath("$[0].vencimento").value(cenario.hoje().plusDays(7).toString()));

		emissao.processar();

		String gestao = cenario.token(sindico);
		String fatura = JsonPath.read(mvc.perform(get("/api/assinatura").header("Authorization", gestao))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ATIVA"))
				.andExpect(jsonPath("$.preco").value(199.90))
				.andExpect(jsonPath("$.suspensaPorFaltaDePagamento").value(false))
				.andExpect(jsonPath("$.faturas", hasSize(1)))
				.andExpect(jsonPath("$.faturas[0].situacao").value("EMITIDA"))
				.andExpect(jsonPath("$.faturas[0].atrasada").value(false))
				.andExpect(jsonPath("$.faturas[0].suspensaoEm").value(cenario.hoje().plusDays(7 + 16).toString()))
				.andReturn().getResponse().getContentAsString(), "$.faturas[0].id");
		mvc.perform(get("/api/assinatura/faturas/{id}/pagamento", fatura).header("Authorization", gestao))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.valor").value(199.90))
				.andExpect(jsonPath("$.pixCopiaECola").value(startsWith("000201")))
				.andExpect(jsonPath("$.gateway").value("SIMULADO"));

		// A rotina roda de hora em hora: repetir não gera outra fatura do mesmo período.
		rotina();
		mvc.perform(get("/api/plataforma/faturas").param("condominioId", condominio.toString()).header("Authorization", admin))
				.andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void semCnpjOBoletoFalhaEOAdministradorTentaDeNovoDepoisDeCadastrar() throws Exception {
		encerrarOTesteHoje();
		emissao.processar();
		String fatura = unicaFatura();
		mvc.perform(get("/api/plataforma/faturas").param("condominioId", condominio.toString()).header("Authorization", admin))
				.andExpect(jsonPath("$[0].situacao").value("FALHOU"))
				.andExpect(jsonPath("$[0].erro").value(startsWith("Falta o CNPJ do condomínio")));

		dadosParaAFatura(cnpj.substring(0, 2) + "." + cnpj.substring(2, 5) + "." + cnpj.substring(5, 8) + "/"
				+ cnpj.substring(8, 12) + "-" + cnpj.substring(12));
		// O mesmo CNPJ em outro cliente é recusado.
		UUID outro = cenario.condominio("Outro com o mesmo CNPJ " + UUID.randomUUID());
		enviar(put("/api/plataforma/condominios/{id}/dados", outro), admin, """
				{"cnpj": "%s"}""".formatted(cnpj))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("Já existe outro cliente com este CNPJ"));
		enviar(post("/api/plataforma/faturas/{id}/nova-tentativa", fatura), admin, "{}").andExpect(status().isNoContent());
		emissao.processar();

		mvc.perform(get("/api/plataforma/faturas").param("condominioId", condominio.toString()).header("Authorization", admin))
				.andExpect(jsonPath("$[0].situacao").value("EMITIDA"))
				.andExpect(jsonPath("$[0].erro").doesNotExist());
	}

	@Test
	void oPagamentoDaBaixaSozinhoEOAvisoRepetidoNaoFazNada() throws Exception {
		String fatura = faturaEmitida();
		mvc.perform(post("/api/avisos/asaas/assinaturas").header("asaas-access-token", "token-errado")
						.contentType(MediaType.APPLICATION_JSON).content("""
								{"event": "PAYMENT_RECEIVED", "payment": {"id": "pay_1"}}"""))
				.andExpect(status().isUnauthorized());
		// Token certo, mas a conta é do simulado: o aviso do Asaas é aceito e ignorado (200, para o Asaas não repetir).
		mvc.perform(post("/api/avisos/asaas/assinaturas").header("asaas-access-token", tokenDoAviso)
						.contentType(MediaType.APPLICATION_JSON).content("""
								{"event": "PAYMENT_RECEIVED", "payment": {"id": "pay_1"}}"""))
				.andExpect(status().isOk());

		pagarNoSimulado(fatura).andExpect(jsonPath("$.resultado").value("PAGA"));

		mvc.perform(get("/api/plataforma/faturas").param("condominioId", condominio.toString()).header("Authorization", admin))
				.andExpect(jsonPath("$[0].situacao").value("PAGA"))
				.andExpect(jsonPath("$[0].pagoEm").value(cenario.hoje().toString()))
				.andExpect(jsonPath("$[0].forma").value("PIX"))
				.andExpect(jsonPath("$[0].tarifa").value(1.99));
		String idNoGateway = jdbc.queryForObject("select id_no_gateway from fatura where id = ?::uuid", String.class, fatura);
		assertThat(recebimento.confirmar(TipoDeGateway.SIMULADO, idNoGateway)).isEqualTo(RecebimentoDeFaturas.Resultado.JA_PAGA);
		mvc.perform(get("/api/assinatura/faturas/{id}/pagamento", fatura).header("Authorization", cenario.token(sindico)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("Esta fatura já foi paga."));
	}

	@Test
	void quinzeDiasDeAtrasoSuspendemEAGestaoSoEntraParaPagar() throws Exception {
		String fatura = faturaEmitida();
		Usuario morador = cenario.usuario("morador");
		cenario.perfil(condominio, morador, Perfil.MORADOR);
		atrasar(fatura, 16);

		assertThat(rotina().condominiosSuspensos()).isGreaterThanOrEqualTo(1);

		mvc.perform(get("/api/plataforma/condominios/{id}", condominio).header("Authorization", admin))
				.andExpect(jsonPath("$.ativo").value(false))
				.andExpect(jsonPath("$.motivoSuspensao").value("FALTA_DE_PAGAMENTO"));
		// O morador não entra mais.
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
						{"email": "%s", "senha": "%s"}""".formatted(morador.getEmail(), CenarioDeTeste.SENHA)))
				.andExpect(jsonPath("$.condominios", empty()));
		// O síndico entra, mas só para pagar.
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
						{"email": "%s", "senha": "%s"}""".formatted(sindico.getEmail(), CenarioDeTeste.SENHA)))
				.andExpect(jsonPath("$.condominios[0].pagamentoPendente").value(true));
		String gestao = cenario.token(sindico);
		mvc.perform(get("/api/assinatura").header("Authorization", gestao))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.suspensaPorFaltaDePagamento").value(true))
				.andExpect(jsonPath("$.faturas[0].atrasada").value(true));
		mvc.perform(get("/api/assinatura/faturas/{id}/pagamento", fatura).header("Authorization", gestao))
				.andExpect(status().isOk());
		mvc.perform(get("/api/unidades").header("Authorization", gestao)).andExpect(status().isForbidden());

		pagarNoSimulado(fatura).andExpect(jsonPath("$.resultado").value("PAGA"));

		mvc.perform(get("/api/plataforma/condominios/{id}", condominio).header("Authorization", admin))
				.andExpect(jsonPath("$.ativo").value(true))
				.andExpect(jsonPath("$.motivoSuspensao").doesNotExist());
		mvc.perform(get("/api/plataforma/condominios/{id}/historico", condominio).header("Authorization", admin))
				.andExpect(jsonPath("$[0].tipo").value("REATIVACAO_APOS_PAGAMENTO"))
				.andExpect(jsonPath("$[1].tipo").value("SUSPENSAO_POR_FALTA_DE_PAGAMENTO"));
		mvc.perform(get("/api/unidades").header("Authorization", cenario.token(sindico))).andExpect(status().isOk());
	}

	@Test
	void suspensaoManualNaoVoltaComOPagamentoEPerdoarAFaturaReativaQuemEstavaSuspensoPorAtraso() throws Exception {
		String fatura = faturaEmitida();
		atrasar(fatura, 20);
		rotina();
		// O administrador suspende à mão um condomínio já suspenso por atraso: só ele reativa.
		enviar(post("/api/plataforma/condominios/{id}/suspensao", condominio), admin, "{}").andExpect(status().isNoContent());
		pagarNoSimulado(fatura).andExpect(jsonPath("$.resultado").value("PAGA"));
		mvc.perform(get("/api/plataforma/condominios/{id}", condominio).header("Authorization", admin))
				.andExpect(jsonPath("$.ativo").value(false))
				.andExpect(jsonPath("$.motivoSuspensao").value("MANUAL"));

		// Outro cliente, suspenso por atraso: perdoar a fatura devolve o acesso, e o período não é cobrado de novo.
		enviar(post("/api/plataforma/condominios/{id}/reativacao", condominio), admin, "{}").andExpect(status().isNoContent());
		UUID outro = cenario.condominio("Residencial Perdoado " + UUID.randomUUID());
		condominio = outro;
		cnpj = cnpjAleatorio();
		String devida = faturaEmitida();
		atrasar(devida, 20);
		rotina();
		enviar(post("/api/plataforma/faturas/{id}/cancelamento", devida), admin, "{}").andExpect(status().isNoContent());
		mvc.perform(get("/api/plataforma/condominios/{id}", outro).header("Authorization", admin))
				.andExpect(jsonPath("$.ativo").value(true));
		emissao.processar(); // cancela o boleto no gateway
		mvc.perform(get("/api/plataforma/faturas").param("condominioId", outro.toString()).header("Authorization", admin))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].situacao").value("CANCELADA"));
	}

	@Test
	void trocaDePlanoValeNaProximaFaturaEOTesteNaoRecomecaAoTrocar() throws Exception {
		// Trocar de plano no teste continua o mesmo prazo.
		enviar(put("/api/plataforma/condominios/{id}/fim-do-teste", condominio), admin, """
				{"fimDoTeste": "%s"}""".formatted(cenario.hoje().plusDays(5))).andExpect(status().isOk());
		enviar(put("/api/plataforma/condominios/{id}/assinatura", condominio), admin, """
				{"plano": "ESSENCIAL", "periodicidade": "MENSAL", "status": "TESTE"}""")
				.andExpect(jsonPath("$.assinatura.fimDoTeste").value(cenario.hoje().plusDays(5).toString()));
		enviar(put("/api/plataforma/condominios/{id}/assinatura", condominio), admin, """
				{"plano": "COMPLETO", "periodicidade": "MENSAL", "status": "TESTE"}""").andExpect(status().isOk());

		String primeira = faturaEmitida();
		enviar(put("/api/plataforma/condominios/{id}/assinatura", condominio), admin, """
				{"plano": "ESSENCIAL", "periodicidade": "SEMESTRAL", "status": "ATIVA"}""").andExpect(status().isOk());
		rotina();
		mvc.perform(get("/api/plataforma/faturas").param("condominioId", condominio.toString()).header("Authorization", admin))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].valor").value(199.90));

		// Chegou a hora da próxima: sai com o plano novo, começando onde a anterior terminou.
		jdbc.update("update fatura set periodo_fim = ? where id = ?::uuid", cenario.hoje().plusDays(5), primeira);
		rotina();
		mvc.perform(get("/api/plataforma/faturas").param("condominioId", condominio.toString()).header("Authorization", admin))
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].plano").value("Essencial"))
				.andExpect(jsonPath("$[0].periodicidade").value("SEMESTRAL"))
				.andExpect(jsonPath("$[0].valor").value(549.00))
				.andExpect(jsonPath("$[0].periodoInicio").value(cenario.hoje().plusDays(5).toString()))
				.andExpect(jsonPath("$[0].periodoFim").value(cenario.hoje().plusDays(5).plusMonths(6).toString()));
	}

	@Test
	void outroCondominioNaoVeAsFaturas() throws Exception {
		String fatura = faturaEmitida();
		UUID vizinho = cenario.condominio("Condomínio Vizinho " + UUID.randomUUID());
		String sindicoVizinho = cenario.token(cenario.comPapel(vizinho, Perfil.SINDICO));

		mvc.perform(get("/api/assinatura").header("Authorization", sindicoVizinho))
				.andExpect(jsonPath("$.faturas", empty()));
		mvc.perform(get("/api/assinatura/faturas/{id}/pagamento", fatura).header("Authorization", sindicoVizinho))
				.andExpect(status().isNotFound());
		// Morador não vê a assinatura do condomínio.
		Usuario morador = cenario.usuario("morador");
		cenario.perfil(condominio, morador, Perfil.MORADOR);
		mvc.perform(get("/api/assinatura").header("Authorization", cenario.token(morador))).andExpect(status().isForbidden());
	}

	// ----- ajudantes -----

	private static String cnpjAleatorio() {
		int[] d = new int[14];
		var aleatorio = new java.util.Random();
		for (int i = 0; i < 12; i++) {
			d[i] = aleatorio.nextInt(10);
		}
		d[12] = digito(d, new int[] { 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2 });
		d[13] = digito(d, new int[] { 6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2 });
		var texto = new StringBuilder();
		for (int x : d) {
			texto.append(x);
		}
		return texto.toString();
	}

	private static int digito(int[] d, int[] pesos) {
		int soma = 0;
		for (int i = 0; i < pesos.length; i++) {
			soma += d[i] * pesos[i];
		}
		return soma % 11 < 2 ? 0 : 11 - soma % 11;
	}

	/** @param informado o CNPJ deste teste, com ou sem pontuação */
	private void dadosParaAFatura(String informado) throws Exception {
		enviar(put("/api/plataforma/condominios/{id}/dados", condominio), admin, """
				{"cnpj": "%s", "email": "financeiro@condominio.com"}""".formatted(informado))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cnpj").value(cnpj));
	}

	/** O fim do teste chega hoje e a rotina roda: a assinatura vira ativa e ganha a primeira fatura. */
	private void encerrarOTesteHoje() throws Exception {
		enviar(put("/api/plataforma/condominios/{id}/fim-do-teste", condominio), admin, """
				{"fimDoTeste": "%s"}""".formatted(cenario.hoje())).andExpect(status().isOk());
		assertThat(rotina().testesEncerrados()).isGreaterThanOrEqualTo(1);
	}

	private String faturaEmitida() throws Exception {
		dadosParaAFatura(cnpj);
		encerrarOTesteHoje();
		emissao.processar();
		String fatura = unicaFatura();
		mvc.perform(get("/api/plataforma/faturas").param("condominioId", condominio.toString()).header("Authorization", admin))
				.andExpect(jsonPath("$[0].situacao").value("EMITIDA"));
		return fatura;
	}

	private String unicaFatura() throws Exception {
		List<String> ids = JsonPath.read(mvc.perform(get("/api/plataforma/faturas").param("condominioId", condominio.toString())
				.header("Authorization", admin)).andReturn().getResponse().getContentAsString(), "$[*].id");
		assertThat(ids).hasSize(1);
		return ids.getFirst();
	}

	/** O vencimento fica {@code dias} no passado. */
	private void atrasar(String fatura, int dias) {
		jdbc.update("update fatura set vencimento = ? where id = ?::uuid", cenario.hoje().minusDays(dias), fatura);
	}

	private FaturasDosClientes.ResumoDaRotina rotina() throws Exception {
		String resposta = enviar(post("/api/plataforma/faturas/rotina"), admin, "{}").andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return new FaturasDosClientes.ResumoDaRotina(JsonPath.read(resposta, "$.testesEncerrados"),
				JsonPath.read(resposta, "$.faturasGeradas"), JsonPath.read(resposta, "$.condominiosSuspensos"));
	}

	private ResultActions pagarNoSimulado(String fatura) throws Exception {
		return enviar(post("/api/plataforma/faturas/{id}/pagamento-simulado", fatura), admin, "{}").andExpect(status().isOk());
	}

	private ResultActions enviar(MockHttpServletRequestBuilder pedido, String token, String corpo) throws Exception {
		return mvc.perform(pedido.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(corpo));
	}

}
