package br.com.condomix.administracao;

import br.com.condomix.acesso.ConviteGerado;
import br.com.condomix.acesso.IngressoDeSindico;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "Plataforma: condomínios")
@RestController
@RequestMapping("/api/plataforma/condominios/{id}/sindico")
class SindicoDoClienteController {

	private final SindicoDoCliente sindico;

	SindicoDoClienteController(SindicoDoCliente sindico) {
		this.sindico = sindico;
	}

	record PedidoNovoConvite(@Email @Size(max = 150) String email) {
	}

	record PedidoNovoSindico(@NotBlank @Size(max = 150) String nome, @NotBlank @Email String email, String documento,
			@NotNull LocalDate inicioMandato, @NotNull LocalDate fimMandato) {
	}

	@Operation(summary = "O síndico do cliente: nome, e-mail, mandato e se já criou o login",
			description = "204 quando o condomínio ainda não tem síndico registrado.")
	@GetMapping
	ResponseEntity<IngressoDeSindico.SindicoAtual> atual(@PathVariable UUID id) {
		return sindico.atual(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
	}

	@Operation(summary = "Novo convite para o síndico que ainda não criou o login",
			description = "O convite anterior deixa de valer. Informe o e-mail só para corrigir o do cadastro.")
	@PostMapping("/convite")
	@ResponseStatus(HttpStatus.CREATED)
	ConviteGerado reconvidar(@PathVariable UUID id, @Valid @RequestBody PedidoNovoConvite pedido,
			@AuthenticationPrincipal Jwt jwt) {
		return sindico.reconvidar(id, pedido.email(), UUID.fromString(jwt.getSubject()));
	}

	@Operation(summary = "Trocar o síndico: registra o novo mandato e o convite",
			description = "O mandato anterior termina na véspera da posse. Se a pessoa já tem login, não há convite.")
	@PutMapping
	IngressoDeSindico.SindicoRegistrado trocar(@PathVariable UUID id, @Valid @RequestBody PedidoNovoSindico pedido,
			@AuthenticationPrincipal Jwt jwt) {
		return sindico.trocar(id, new IngressoDeSindico.NovoSindico(pedido.nome(), pedido.email(), pedido.documento(),
				pedido.inicioMandato(), pedido.fimMandato()), UUID.fromString(jwt.getSubject()));
	}

}
