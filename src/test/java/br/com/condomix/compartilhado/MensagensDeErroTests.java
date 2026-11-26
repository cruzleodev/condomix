package br.com.condomix.compartilhado;

import br.com.condomix.CenarioDeTeste;
import br.com.condomix.TestcontainersConfiguration;
import br.com.condomix.acesso.Perfil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Os erros que o próprio Spring gera (valor inválido, JSON quebrado) chegam à tela em português. */
@Import({ TestcontainersConfiguration.class, CenarioDeTeste.class })
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MensagensDeErroTests {

	@Autowired MockMvc mvc;
	@Autowired CenarioDeTeste cenario;

	@Test
	void valorInvalidoNoFiltroVemEmPortugues() throws Exception {
		String token = cenario.token(cenario.comPapel(cenario.condominio("Residencial dos Erros " + UUID.randomUUID()),
				Perfil.MORADOR));
		mvc.perform(get("/api/financeiro/balancete").param("competencia", "2fff").header("Authorization", token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail")
						.value("O valor \"2fff\" não é válido. Confira o que foi preenchido e tente de novo."));
	}

	@Test
	void dadosForaDoFormatoVemEmPortugues() throws Exception {
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{isso não é json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(
						"Os dados enviados não estão no formato esperado. Confira os campos e tente de novo."));
	}

}
