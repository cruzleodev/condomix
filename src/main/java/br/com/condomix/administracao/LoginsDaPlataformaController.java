package br.com.condomix.administracao;

import br.com.condomix.acesso.LoginsDaPlataforma;
import br.com.condomix.acesso.LoginsDaPlataforma.LoginNaPlataforma;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Plataforma: logins")
@RestController
@RequestMapping("/api/plataforma/logins")
class LoginsDaPlataformaController {

	private final LoginsDaPlataforma logins;

	LoginsDaPlataformaController(LoginsDaPlataforma logins) {
		this.logins = logins;
	}

	record PedidoBloqueio(@NotBlank @Size(min = 10, max = 500) String motivo) {
	}

	@Operation(summary = "Buscar logins por parte do e-mail (até 20)",
			description = "Mostra a situação e em que condomínios o login entra, com os papéis. Nada dos dados internos.")
	@GetMapping
	List<LoginNaPlataforma> buscar(@RequestParam String email) {
		return logins.buscar(email);
	}

	@Operation(summary = "Bloquear um login em todos os condomínios",
			description = "O usuário não entra mais, e os tokens que ele já tinha deixam de valer na hora.")
	@PostMapping("/{id}/bloqueio")
	LoginNaPlataforma bloquear(@PathVariable UUID id, @Valid @RequestBody PedidoBloqueio pedido,
			@AuthenticationPrincipal Jwt jwt) {
		return logins.bloquear(id, pedido.motivo(), UUID.fromString(jwt.getSubject()));
	}

	@Operation(summary = "Desbloquear um login")
	@PostMapping("/{id}/desbloqueio")
	LoginNaPlataforma desbloquear(@PathVariable UUID id) {
		return logins.desbloquear(id);
	}

}
