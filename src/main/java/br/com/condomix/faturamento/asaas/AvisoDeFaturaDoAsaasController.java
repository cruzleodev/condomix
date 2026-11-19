package br.com.condomix.faturamento.asaas;

import br.com.condomix.faturamento.aplicacao.RecebimentoDeFaturas;
import br.com.condomix.pagamentos.TipoDeGateway;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Aviso (webhook) do Asaas na conta do Condomix: "o pagamento de uma fatura mudou". Aberto (sem login): a
 * autenticidade vem do token que o Asaas manda no cabeçalho {@code asaas-access-token}. O corpo só diz qual
 * pagamento conferir; o valor e a situação vêm da API do Asaas.
 */
@Tag(name = "Avisos dos gateways")
@RestController
class AvisoDeFaturaDoAsaasController {

	private static final Logger log = LoggerFactory.getLogger(AvisoDeFaturaDoAsaasController.class);

	private static final Set<String> RECEBIMENTOS = Set.of("PAYMENT_RECEIVED", "PAYMENT_CONFIRMED", "PAYMENT_RECEIVED_IN_CASH");

	private final RecebimentoDeFaturas recebimento;

	AvisoDeFaturaDoAsaasController(RecebimentoDeFaturas recebimento) {
		this.recebimento = recebimento;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record AvisoDeFaturaDoAsaas(String event, PagamentoDaFaturaNoAviso payment) {

		@JsonIgnoreProperties(ignoreUnknown = true)
		record PagamentoDaFaturaNoAviso(String id) {
		}

	}

	@Operation(summary = "Aviso de pagamento do Asaas (configurado na conta do Condomix)",
			description = "Responde 200 para todo aviso autêntico, mesmo os ignorados, para o Asaas não repetir.")
	@PostMapping("/api/avisos/asaas/assinaturas")
	ResponseEntity<Void> aviso(@RequestHeader(name = "asaas-access-token", required = false) String token,
			@RequestBody AvisoDeFaturaDoAsaas aviso) {
		if (!recebimento.avisoAutentico(token)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		if (aviso.payment() != null && aviso.payment().id() != null && RECEBIMENTOS.contains(aviso.event())) {
			var resultado = recebimento.confirmar(TipoDeGateway.ASAAS, aviso.payment().id());
			log.info("Aviso do Asaas {} para a fatura com pagamento {}: {}", aviso.event(), aviso.payment().id(), resultado);
		}
		return ResponseEntity.ok().build();
	}

}
