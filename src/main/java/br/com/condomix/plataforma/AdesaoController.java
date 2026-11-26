package br.com.condomix.plataforma;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** A data de adesão do condomínio ao Condomix: as telas não deixam escolher datas anteriores. */
@Tag(name = "Condomínio")
@RestController
class AdesaoController {

	private final AdesaoAoCondomix adesao;

	AdesaoController(AdesaoAoCondomix adesao) {
		this.adesao = adesao;
	}

	/**
	 * @param data              o dia da adesão (mínimo para a posse de mandatos)
	 * @param inicioDoHistorico o primeiro dia aceito no caixa (12 meses antes, no 1º dia do mês)
	 */
	record AdesaoDoCondominio(LocalDate data, LocalDate inicioDoHistorico) {
	}

	@Operation(summary = "Quando o condomínio aderiu ao Condomix", description = "Datas anteriores são recusadas nos registros.")
	@GetMapping("/api/condominio/adesao")
	AdesaoDoCondominio adesao() {
		return new AdesaoDoCondominio(adesao.data(), adesao.inicioDoHistorico());
	}

}
