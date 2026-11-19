package br.com.condomix.financeiro.asaas;

import br.com.condomix.pagamentos.TipoDeGateway;
import br.com.condomix.financeiro.aplicacao.RecebimentoPeloGateway;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

/**
 * Aviso (webhook) do Asaas: "um pagamento mudou". Aberto (sem login): a autenticidade vem do token que o
 * Asaas manda no cabeçalho {@code asaas-access-token}, configurado na conta de cada condomínio.
 * O corpo só serve para saber qual pagamento conferir: o valor e a situação vêm da API do Asaas.
 */
@Tag(name = "Avisos dos gateways")
@RestController
class AvisoDoAsaasController {

	private static final Logger log = LoggerFactory.getLogger(AvisoDoAsaasController.class);

	/** Eventos que podem significar dinheiro recebido. Os outros (criado, vencido...) são ignorados. */
	private static final Set<String> RECEBIMENTOS = Set.of("PAYMENT_RECEIVED", "PAYMENT_CONFIRMED", "PAYMENT_RECEIVED_IN_CASH");

	private final RecebimentoPeloGateway recebimento;

	AvisoDoAsaasController(RecebimentoPeloGateway recebimento) {
		this.recebimento = recebimento;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record AvisoDoAsaas(String event, PagamentoDoAviso payment) {

		@JsonIgnoreProperties(ignoreUnknown = true)
		record PagamentoDoAviso(String id) {
		}

	}

	@Operation(summary = "Aviso de pagamento do Asaas (configurado na conta do condomínio)",
			description = "Responde 200 para todo aviso autêntico, mesmo os ignorados, para o Asaas não repetir.")
	@PostMapping("/api/avisos/asaas/{condominioId}")
	ResponseEntity<Void> aviso(@PathVariable UUID condominioId,
			@RequestHeader(name = "asaas-access-token", required = false) String token, @RequestBody AvisoDoAsaas aviso) {
		if (!recebimento.avisoAutentico(condominioId, token)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		if (aviso.payment() != null && aviso.payment().id() != null && RECEBIMENTOS.contains(aviso.event())) {
			var resultado = recebimento.confirmar(condominioId, TipoDeGateway.ASAAS, aviso.payment().id());
			log.info("Aviso do Asaas {} para o pagamento {} do condomínio {}: {}", aviso.event(), aviso.payment().id(),
					condominioId, resultado);
		}
		return ResponseEntity.ok().build();
	}

}
