package br.com.condomix.reservas;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reservas: cadastro de espaços, agenda, reserva na hora, regras, aluguel como cobrança avulsa, cancelamento no prazo
 * e fora dele, e separação por condomínio.
 */
@Import({ TestcontainersConfiguration.class, CenarioDeTeste.class })
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReservasTests {

	@Autowired MockMvc mvc;
	@Autowired CenarioDeTeste cenario;

	UUID condominio;
	String sindico;
	String unidadeAna;
	String unidadeBeto;
	String ana;
	String beto;

	@BeforeEach
	void prepararCondominioComDuasUnidades() throws Exception {
		condominio = cenario.condominio("Residencial das Reservas " + UUID.randomUUID());
		sindico = cenario.token(cenario.comPapel(condominio, Perfil.SINDICO));
		enviar(put("/api/financeiro/configuracao"), sindico, """
				{"tipoRateio": "IGUALITARIO", "diaVencimento": 10, "multaPercentual": 2, "jurosMensalPercentual": 1,
				 "fundoReservaPercentual": 0}""").andExpect(status().isOk());
		unidadeAna = unidade("101");
		unidadeBeto = unidade("102");
		ana = morador("Ana", unidadeAna);
		beto = morador("Beto", unidadeBeto);
	}

	@Test
	void sindicoCadastraOEspacoEOsHorariosNaoPodemSeSobrepor() throws Exception {
		enviar(post("/api/espacos"), sindico, espaco("Salão", 0, "", """
				{"nome": "Dia inteiro", "inicio": "08:00", "fim": "22:00"}, {"nome": "Noite", "inicio": "18:00", "fim": "23:00"}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Os horários \"Dia inteiro\" e \"Noite\" se sobrepõem"));
		String salao = espacoPadrao("Salão de festas", 0, "");
		enviar(post("/api/espacos"), sindico, espaco("salão de festas", 0, "", HORARIOS)).andExpect(status().isConflict());
		mvc.perform(get("/api/espacos").header("Authorization", ana))
				.andExpect(jsonPath("$[0].nome").value("Salão de festas"))
				.andExpect(jsonPath("$[0].horarios", hasSize(2)))
				.andExpect(jsonPath("$[0].horarios[0].nome").value("Almoço"));
		// Morador não cadastra; desativado some para o morador.
		enviar(post("/api/espacos"), ana, espaco("Quadra", 0, "", HORARIOS)).andExpect(status().isForbidden());
		enviar(post("/api/espacos/{id}/desativacao", salao), sindico, "{}").andExpect(jsonPath("$.ativo").value(false));
		mvc.perform(get("/api/espacos").header("Authorization", ana)).andExpect(jsonPath("$", empty()));
		mvc.perform(get("/api/espacos").header("Authorization", sindico)).andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void reservaValeNaHoraEOMesmoHorarioNaoEReservadoDuasVezes() throws Exception {
		String salao = espacoPadrao("Churrasqueira", 0, "");
		LocalDate dia = cenario.hoje().plusDays(3);
		String reserva = JsonPath.read(reservar(ana, salao, dia, "Noite", unidadeAna)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.situacao").value("CONFIRMADA"))
				.andExpect(jsonPath("$.unidade").value("101"))
				.andExpect(jsonPath("$.horario").value("Noite"))
				.andExpect(jsonPath("$.inicio").value("18:00:00"))
				.andExpect(jsonPath("$.cobrancaId").doesNotExist())
				.andReturn().getResponse().getContentAsString(), "$.id");

		reservar(beto, salao, dia, "noite", unidadeBeto)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value(startsWith("Este horário já está reservado")));
		reservar(beto, salao, dia, "Almoço", unidadeBeto).andExpect(status().isCreated());

		// Na agenda: a Ana vê a dela; o Beto vê ocupado, sem saber de quem; o síndico vê a unidade.
		agenda(ana, salao, dia).andExpect(jsonPath("$[0].horarios[1].situacao").value("DA_MINHA_UNIDADE"))
				.andExpect(jsonPath("$[0].horarios[1].reservaId").value(reserva))
				.andExpect(jsonPath("$[0].horarios[0].situacao").value("OCUPADO"))
				.andExpect(jsonPath("$[0].horarios[0].unidade").doesNotExist());
		agenda(sindico, salao, dia).andExpect(jsonPath("$[0].horarios[1].unidade").value("101"));
		mvc.perform(get("/api/eu/reservas").header("Authorization", ana))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].espaco").value("Churrasqueira"));
		mvc.perform(get("/api/reservas").param("de", dia.toString()).param("ate", dia.toString()).header("Authorization", sindico))
				.andExpect(jsonPath("$", hasSize(2)));
		mvc.perform(get("/api/reservas").param("de", dia.toString()).param("ate", dia.toString()).header("Authorization", ana))
				.andExpect(status().isForbidden());
	}

	@Test
	void moradorSoReservaECancelaParaASuaUnidade() throws Exception {
		String salao = espacoPadrao("Salão", 0, "");
		LocalDate dia = cenario.hoje().plusDays(2);
		mvc.perform(get("/api/reservas/unidades").header("Authorization", ana))
				.andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].descricao").value("101"));
		mvc.perform(get("/api/reservas/unidades").header("Authorization", sindico)).andExpect(jsonPath("$", hasSize(2)));
		reservar(ana, salao, dia, "Noite", unidadeBeto)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("só pode reservar para uma unidade")));
		String daBeto = JsonPath.read(reservar(sindico, salao, dia, "Noite", unidadeBeto).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.id");
		enviar(post("/api/reservas/{id}/cancelamento", daBeto), ana, "{}").andExpect(status().isNotFound());
		enviar(post("/api/reservas/{id}/cancelamento", daBeto), beto, "{\"motivo\": \"Mudei de ideia\"}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.aluguel").value("SEM_ALUGUEL"));
		enviar(post("/api/reservas/{id}/cancelamento", daBeto), beto, "{}").andExpect(status().isConflict());
		// Cancelado, o horário fica livre de novo.
		reservar(ana, salao, dia, "Noite", unidadeAna).andExpect(status().isCreated());
	}

	@Test
	void aluguelViraCobrancaAvulsaECancelarNoPrazoCancelaACobranca() throws Exception {
		String salao = espacoPadrao("Salão de festas", 150, "");
		LocalDate dia = cenario.hoje().plusDays(5);
		String resposta = reservar(ana, salao, dia, "Noite", unidadeAna).andExpect(status().isCreated())
				.andExpect(jsonPath("$.valor").value(150.00))
				.andExpect(jsonPath("$.cancelavelSemCustoAte").value(dia.toString()))
				.andReturn().getResponse().getContentAsString();
		String reserva = JsonPath.read(resposta, "$.id");
		String cobranca = JsonPath.read(resposta, "$.cobrancaId");
		mvc.perform(get("/api/eu/cobrancas").header("Authorization", ana))
				.andExpect(jsonPath("$[?(@.id == '%s')].descricao".formatted(cobranca),
						hasItem(startsWith("Aluguel: Salão de festas, "))))
				.andExpect(jsonPath("$[?(@.id == '%s')].vencimento".formatted(cobranca), hasItem(dia.toString())))
				.andExpect(jsonPath("$[?(@.id == '%s')].valor".formatted(cobranca), hasItem(150.0)));

		enviar(post("/api/reservas/{id}/cancelamento", reserva), ana, "{}").andExpect(jsonPath("$.aluguel").value("CANCELADO"));
		mvc.perform(get("/api/cobrancas/{id}", cobranca).header("Authorization", sindico))
				.andExpect(jsonPath("$.status").value("CANCELADA"));
	}

	@Test
	void foraDoPrazoOAluguelContinuaDevidoMasASindicoPodeDispensar() throws Exception {
		String salao = espacoPadrao("Salão de festas", 200, "\"diasParaCancelar\": 10,");
		LocalDate dia = cenario.hoje().plusDays(5);
		String resposta = reservar(ana, salao, dia, "Noite", unidadeAna)
				.andExpect(jsonPath("$.cancelavelSemCustoAte").value(dia.minusDays(10).toString()))
				.andReturn().getResponse().getContentAsString();
		enviar(post("/api/reservas/{id}/cancelamento", (String) JsonPath.read(resposta, "$.id")), ana, "{\"dispensarAluguel\": true}")
				.andExpect(jsonPath("$.aluguel").value("MANTIDO"));
		mvc.perform(get("/api/cobrancas/{id}", (String) JsonPath.read(resposta, "$.cobrancaId")).header("Authorization", sindico))
				.andExpect(jsonPath("$.status").value("ABERTA"));
		mvc.perform(get("/api/eu/reservas").header("Authorization", ana))
				.andExpect(jsonPath("$[0].situacao").value("CANCELADA"))
				.andExpect(jsonPath("$[0].aluguelMantido").value(true));

		String outra = reservar(beto, salao, dia, "Noite", unidadeBeto).andReturn().getResponse().getContentAsString();
		enviar(post("/api/reservas/{id}/cancelamento", (String) JsonPath.read(outra, "$.id")), sindico,
				"{\"motivo\": \"Salão interditado\", \"dispensarAluguel\": true}")
				.andExpect(jsonPath("$.aluguel").value("CANCELADO"));
	}

	@Test
	void regrasDeAntecedenciaLimitePorMesEInadimplencia() throws Exception {
		String quadra = espacoPadrao("Quadra", 0, "\"antecedenciaMinimaDias\": 2, \"antecedenciaMaximaDias\": 30, \"limitePorMes\": 1,");
		reservar(ana, quadra, cenario.hoje().plusDays(1), "Noite", unidadeAna)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(startsWith("Este espaço precisa ser reservado com pelo menos 2 dias de antecedência")));
		reservar(ana, quadra, cenario.hoje().plusDays(31), "Noite", unidadeAna)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(startsWith("Este espaço só pode ser reservado até 30 dias à frente")));
		reservar(ana, quadra, cenario.hoje().minusDays(1), "Noite", unidadeAna)
				.andExpect(jsonPath("$.detail").value("Não dá para reservar uma data que já passou."));
		// Limite de 1 por mês: escolhe dois dias do mesmo mês dentro da antecedência.
		LocalDate primeiro = cenario.hoje().plusDays(2);
		LocalDate segundo = primeiro.getDayOfMonth() < primeiro.lengthOfMonth() ? primeiro.plusDays(1) : primeiro.plusDays(2);
		LocalDate mesmoMes = segundo.getMonth() == primeiro.getMonth() ? segundo : segundo.plusDays(1);
		reservar(ana, quadra, mesmoMes, "Noite", unidadeAna).andExpect(status().isCreated());
		reservar(ana, quadra, mesmoMes, "Almoço", unidadeAna)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("o limite do condomínio")));

		// Cobrança vencida em aberto: o Beto não reserva até regularizar.
		enviar(post("/api/cobrancas"), sindico, """
				{"unidadeId": "%s", "tipo": "MULTA_DISCIPLINAR", "descricao": "Multa", "valor": 50, "vencimento": "%s"}"""
				.formatted(unidadeBeto, cenario.hoje().minusDays(3))).andExpect(status().isCreated());
		reservar(beto, quadra, mesmoMes, "Almoço", unidadeBeto)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(startsWith("A unidade tem cobrança vencida em aberto")));
		// Num espaço que não bloqueia inadimplente, ele reserva.
		String churrasqueira = espacoPadrao("Churrasqueira", 0, "\"bloqueiaInadimplente\": false,");
		reservar(beto, churrasqueira, cenario.hoje().plusDays(2), "Noite", unidadeBeto).andExpect(status().isCreated());
	}

	@Test
	void outroCondominioNaoVeOsEspacosNemAsReservas() throws Exception {
		String salao = espacoPadrao("Salão", 0, "");
		String reserva = JsonPath.read(reservar(ana, salao, cenario.hoje().plusDays(2), "Noite", unidadeAna)
				.andReturn().getResponse().getContentAsString(), "$.id");
		UUID vizinho = cenario.condominio("Condomínio Vizinho " + UUID.randomUUID());
		String sindicoVizinho = cenario.token(cenario.comPapel(vizinho, Perfil.SINDICO));
		mvc.perform(get("/api/espacos").header("Authorization", sindicoVizinho)).andExpect(jsonPath("$", empty()));
		mvc.perform(get("/api/espacos/{id}", salao).header("Authorization", sindicoVizinho)).andExpect(status().isNotFound());
		enviar(post("/api/reservas/{id}/cancelamento", reserva), sindicoVizinho, "{}").andExpect(status().isNotFound());
		mvc.perform(get("/api/reservas").param("de", cenario.hoje().toString()).param("ate", cenario.hoje().plusDays(10).toString())
				.header("Authorization", sindicoVizinho)).andExpect(jsonPath("$", empty()));
	}

	// ----- ajudantes -----

	private static final String HORARIOS = """
			{"nome": "Almoço", "inicio": "10:00", "fim": "16:00"}, {"nome": "Noite", "inicio": "18:00", "fim": "23:00"}""";

	/** @param regras pedaço de JSON com as regras que mudam do padrão, terminado em vírgula (ou vazio) */
	private static String espaco(String nome, int aluguel, String regras, String horarios) {
		var padrao = new StringBuilder();
		for (String campo : new String[] { "\"antecedenciaMinimaDias\": 0,", "\"antecedenciaMaximaDias\": 90,",
				"\"bloqueiaInadimplente\": true,", "\"diasParaCancelar\": 0," }) {
			if (!regras.contains(campo.substring(0, campo.indexOf(':')))) {
				padrao.append(campo).append(' ');
			}
		}
		return "{\"nome\": \"%s\", \"valorAluguel\": %d, %s %s \"horarios\": [%s]}".formatted(nome, aluguel, padrao, regras, horarios);
	}

	private String espacoPadrao(String nome, int aluguel, String regras) throws Exception {
		return JsonPath.read(enviar(post("/api/espacos"), sindico, espaco(nome, aluguel, regras, HORARIOS))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	private ResultActions reservar(String token, String espaco, LocalDate dia, String horario, String unidade) throws Exception {
		return enviar(post("/api/reservas"), token, """
				{"espacoId": "%s", "data": "%s", "horario": "%s", "unidadeId": "%s"}""".formatted(espaco, dia, horario, unidade));
	}

	private ResultActions agenda(String token, String espaco, LocalDate dia) throws Exception {
		return mvc.perform(get("/api/espacos/{id}/agenda", espaco).param("de", dia.toString()).param("ate", dia.toString())
				.header("Authorization", token)).andExpect(status().isOk());
	}

	private String unidade(String identificacao) throws Exception {
		return JsonPath.read(enviar(post("/api/unidades"), sindico, """
				{"identificacao": "%s", "tipo": "APARTAMENTO"}""".formatted(identificacao)).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.id");
	}

	/** Um morador com login, proprietário da unidade. */
	private String morador(String nome, String unidade) throws Exception {
		Usuario usuario = cenario.usuario(nome);
		cenario.perfil(condominio, usuario, Perfil.MORADOR);
		String pessoa = cenario.pessoa(condominio, nome, usuario).getId().toString();
		enviar(post("/api/pessoas/{id}/vinculos", pessoa), sindico, """
				{"unidadeId": "%s", "tipo": "PROPRIETARIO"}""".formatted(unidade)).andExpect(status().isCreated());
		return cenario.token(usuario);
	}

	private ResultActions enviar(MockHttpServletRequestBuilder pedido, String token, String corpo) throws Exception {
		return mvc.perform(pedido.contentType(MediaType.APPLICATION_JSON).content(corpo).header("Authorization", token));
	}

}
