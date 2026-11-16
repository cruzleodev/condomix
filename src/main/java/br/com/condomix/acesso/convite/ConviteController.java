package br.com.condomix.acesso.convite;

import br.com.condomix.acesso.ConviteGerado;
import br.com.condomix.acesso.Perfil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Convites de acesso. O código do convite vai no corpo (POST), nunca no endereço: endereços ficam em logs.
 */
@Tag(name = "Convites")
@RestController
@RequestMapping("/api/convites")
class ConviteController {

	private final EmissaoDeConvites emissao;

	private final AceiteDeConvites aceite;

	ConviteController(EmissaoDeConvites emissao, AceiteDeConvites aceite) {
		this.emissao = emissao;
		this.aceite = aceite;
	}

	record PedidoConvite(@NotNull UUID pessoaId, @NotBlank @Email String email, Perfil perfil) {
	}

	record PedidoCodigo(@NotBlank String codigo) {
	}

	record PedidoAceite(@NotBlank String codigo, @NotBlank String senha) {
	}

	record ConviteAceito(String email) {
	}

	@Operation(summary = "Convidar uma pessoa do condomínio (síndico ou administradora)",
			description = "perfil: MORADOR, ADMINISTRADORA ou PORTARIA. Deixe vazio para quem já tem um mandato "
					+ "(ex.: síndico). A resposta traz o link para repassar à pessoa; vale 7 dias.")
	@PreAuthorize("hasAnyRole('SINDICO', 'ADMINISTRADORA')")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	ConviteGerado convidar(@Valid @RequestBody PedidoConvite pedido, @AuthenticationPrincipal Jwt jwt) {
		return emissao.convidar(pedido.pessoaId(), pedido.email(), pedido.perfil(), UUID.fromString(jwt.getSubject()));
	}

	@Operation(summary = "Ver um convite pelo código (aberto)")
	@SecurityRequirements
	@PostMapping("/consulta")
	AceiteDeConvites.InformacoesDoConvite consultar(@Valid @RequestBody PedidoCodigo pedido) {
		return aceite.consultar(pedido.codigo());
	}

	@Operation(summary = "Aceitar um convite (aberto)",
			description = "Sem login: cria com esta senha (mínimo 8 caracteres). Já tem login: informe a senha atual.")
	@SecurityRequirements
	@PostMapping("/aceite")
	ConviteAceito aceitar(@Valid @RequestBody PedidoAceite pedido) {
		return new ConviteAceito(aceite.aceitar(pedido.codigo(), pedido.senha()));
	}

}
