package br.com.condomix.administracao;

import br.com.condomix.faturamento.ContaDoCondomixNoGateway;
import br.com.condomix.faturamento.ContaDoCondomixNoGateway.AvisoDaContaDeRecebimento;
import br.com.condomix.faturamento.ContaDoCondomixNoGateway.SituacaoDaContaDeRecebimento;
import br.com.condomix.pagamentos.AmbienteDoGateway;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

/** A conta do Condomix no gateway, que recebe as assinaturas dos clientes. */
@Tag(name = "Plataforma: faturas")
@RestController
@RequestMapping("/api/plataforma/recebimento")
class RecebimentoDoCondomixController {

	private final ContaDoCondomixNoGateway conta;

	RecebimentoDoCondomixController(ContaDoCondomixNoGateway conta) {
		this.conta = conta;
	}

	record PedidoContaDeRecebimento(@NotNull TipoDeGateway gateway, @NotNull AmbienteDoGateway ambiente,
			@NotBlank @Size(max = 500) String chave) {
	}

	/** @param disponiveis os gateways desta instalação (o simulado só em desenvolvimento) */
	record ContaDeRecebimentoAtual(SituacaoDaContaDeRecebimento conta, Set<TipoDeGateway> disponiveis) {
	}

	@Operation(summary = "A conta do Condomix que recebe as assinaturas: se está ligada e por qual gateway")
	@GetMapping
	ContaDeRecebimentoAtual situacao() {
		return new ContaDeRecebimentoAtual(conta.situacao(), conta.disponiveis());
	}

	@Operation(summary = "Ligar a cobrança das assinaturas com a chave de API da conta do Condomix no gateway",
			description = "A chave é conferida no gateway e guardada cifrada. A resposta traz o endereço e o token do aviso "
					+ "(webhook) para cadastrar na conta: o token só aparece agora.")
	@PutMapping
	AvisoDaContaDeRecebimento configurar(@Valid @RequestBody PedidoContaDeRecebimento pedido, @AuthenticationPrincipal Jwt jwt) {
		return conta.configurar(pedido.gateway(), pedido.ambiente(), pedido.chave(), UUID.fromString(jwt.getSubject()));
	}

	@Operation(summary = "Desligar: não sai mais fatura nova (as já emitidas continuam valendo)")
	@PostMapping("/desativacao")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void desativar() {
		conta.desativar();
	}

}
