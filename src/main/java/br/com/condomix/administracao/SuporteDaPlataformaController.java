package br.com.condomix.administracao;

import br.com.condomix.acesso.SuporteAoCondominio;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Plataforma: suporte")
@RestController
@RequestMapping("/api/plataforma/suporte")
class SuporteDaPlataformaController {

	private final SuporteAoCondominio suporte;

	SuporteDaPlataformaController(SuporteAoCondominio suporte) {
		this.suporte = suporte;
	}

	record PedidoSuporte(@NotNull UUID condominioId, @NotBlank @Size(min = 10, max = 500) String motivo) {
	}

	@Operation(summary = "Abrir acesso de suporte a um condomínio",
			description = "Devolve um token só de leitura, válido por 1 hora. O acesso fica registrado com o motivo (LGPD).")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	SuporteAoCondominio.TokenDeSuporte abrir(@Valid @RequestBody PedidoSuporte pedido, @AuthenticationPrincipal Jwt jwt) {
		return suporte.abrir(UUID.fromString(jwt.getSubject()), pedido.condominioId(), pedido.motivo());
	}

	@Operation(summary = "Encerrar um acesso de suporte antes do prazo",
			description = "O token de suporte deixa de valer na hora. Sem efeito se o acesso já terminou.")
	@PostMapping("/{id}/encerramento")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void encerrar(@PathVariable UUID id) {
		suporte.encerrar(id);
	}

	@Operation(summary = "Auditoria: os últimos acessos de suporte")
	@GetMapping
	List<SuporteAoCondominio.RegistroDeSuporte> historico() {
		return suporte.historico();
	}

}
