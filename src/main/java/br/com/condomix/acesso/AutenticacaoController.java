package br.com.condomix.acesso;

import br.com.condomix.compartilhado.TenantAtual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Autenticação")
@RestController
@RequestMapping("/api")
class AutenticacaoController {

	private final AutenticacaoService autenticacao;

	AutenticacaoController(AutenticacaoService autenticacao) {
		this.autenticacao = autenticacao;
	}

	record PedidoLogin(@NotBlank @Email String email, @NotBlank String senha) {
	}

	record PedidoSelecaoCondominio(@NotNull UUID condominioId) {
	}

	record UsuarioLogado(UUID usuarioId, String email, UUID condominioId, List<String> perfis) {
	}

	@Operation(summary = "Entrar com e-mail e senha")
	@PostMapping("/auth/login")
	Sessao entrar(@Valid @RequestBody PedidoLogin pedido) {
		return autenticacao.entrar(pedido.email(), pedido.senha());
	}

	@Operation(summary = "Escolher (ou trocar) o condomínio e receber um novo token")
	@PostMapping("/auth/condominio")
	Sessao selecionarCondominio(@Valid @RequestBody PedidoSelecaoCondominio pedido, @AuthenticationPrincipal Jwt jwt) {
		return autenticacao.selecionarCondominio(UUID.fromString(jwt.getSubject()), pedido.condominioId());
	}

	@Operation(summary = "Quem está logado e em qual condomínio")
	@GetMapping("/eu")
	UsuarioLogado eu(@AuthenticationPrincipal Jwt jwt) {
		List<String> perfis = jwt.hasClaim(ConfiguracaoSeguranca.CLAIM_PERFIS)
				? jwt.getClaimAsStringList(ConfiguracaoSeguranca.CLAIM_PERFIS)
				: List.of();
		return new UsuarioLogado(UUID.fromString(jwt.getSubject()),
				jwt.getClaimAsString(ConfiguracaoSeguranca.CLAIM_EMAIL),
				TenantAtual.id().orElse(null),
				perfis);
	}

}
