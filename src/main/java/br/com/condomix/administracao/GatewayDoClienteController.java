package br.com.condomix.administracao;

import br.com.condomix.pagamentos.AmbienteDoGateway;
import br.com.condomix.financeiro.GatewayDoCondominio;
import br.com.condomix.pagamentos.TipoDeGateway;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Plataforma: condomínios")
@RestController
@RequestMapping("/api/plataforma/condominios/{id}/gateway")
class GatewayDoClienteController {

	private final GatewayDoCliente gateway;

	GatewayDoClienteController(GatewayDoCliente gateway) {
		this.gateway = gateway;
	}

	record PedidoGateway(@NotNull TipoDeGateway gateway, @NotNull AmbienteDoGateway ambiente,
			@NotBlank @Size(max = 500) String chave) {
	}

	@Operation(summary = "Boleto e Pix do cliente: se estão ligados e por qual gateway")
	@GetMapping
	GatewayDoCliente.GatewayDoClienteAtual situacao(@PathVariable UUID id) {
		return gateway.situacao(id);
	}

	@Operation(summary = "Ligar o boleto e o Pix com a chave de API da conta do condomínio no gateway",
			description = "A chave é conferida no gateway e guardada cifrada. A resposta traz o endereço e o token do aviso "
					+ "(webhook) para cadastrar na conta: o token só aparece agora.")
	@PutMapping
	GatewayDoCondominio.AvisoParaConfigurar configurar(@PathVariable UUID id, @Valid @RequestBody PedidoGateway pedido,
			@AuthenticationPrincipal Jwt jwt) {
		return gateway.configurar(id, pedido.gateway(), pedido.ambiente(), pedido.chave(), UUID.fromString(jwt.getSubject()));
	}

	@Operation(summary = "Desligar: as cobranças novas deixam de sair com boleto (os já emitidos continuam valendo)")
	@PostMapping("/desativacao")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void desativar(@PathVariable UUID id) {
		gateway.desativar(id);
	}

}
