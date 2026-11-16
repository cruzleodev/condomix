package br.com.condomix.acesso.mandato;

import br.com.condomix.acesso.Cargo;
import br.com.condomix.acesso.Permissoes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Cargos e mandatos. Ver: todos do condomínio. Registrar e encerrar: síndico ou administradora. */
@Tag(name = "Cargos e mandatos")
@RestController
@RequestMapping("/api/mandatos")
class MandatoController {

	private final RegistroDeMandatos registro;

	private final ConsultaDeMandatos consulta;

	MandatoController(RegistroDeMandatos registro, ConsultaDeMandatos consulta) {
		this.registro = registro;
		this.consulta = consulta;
	}

	record PedidoMandato(
			@NotNull UUID pessoaId,
			@NotNull Cargo cargo,
			@NotNull LocalDate inicio,
			@NotNull LocalDate fimPrevisto,
			UUID ataEleicaoId,
			BigDecimal descontoPercentual,
			UUID unidadeBeneficiadaId) {
	}

	record PedidoEncerramento(@NotNull LocalDate ultimoDia, @NotNull MotivoEncerramento motivo, UUID ataId) {
	}

	@Operation(summary = "Histórico de mandatos do condomínio",
			description = "Use somenteComAcesso=true para ver só quem ocupa os cargos hoje (inclui prorrogados).")
	@GetMapping
	List<MandatoResposta> listar(@RequestParam(defaultValue = "false") boolean somenteComAcesso) {
		return consulta.listar(somenteComAcesso).stream().map(MandatoResposta::de).toList();
	}

	@Operation(summary = "Um mandato")
	@GetMapping("/{id}")
	MandatoResposta obter(@PathVariable UUID id) {
		return MandatoResposta.de(consulta.obter(id));
	}

	@Operation(summary = "Registrar eleição (síndico ou administradora)",
			description = "Para síndico e subsíndico, o mandato do antecessor é encerrado no dia anterior à posse. "
					+ "Mandato de no máximo 2 anos. Desconto: informe percentual e unidade juntos.")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	MandatoResposta registrar(@Valid @RequestBody PedidoMandato pedido, @AuthenticationPrincipal Jwt jwt) {
		var novo = new RegistroDeMandatos.NovoMandato(pedido.pessoaId(), pedido.cargo(), pedido.inicio(),
				pedido.fimPrevisto(), pedido.ataEleicaoId(), pedido.descontoPercentual(), pedido.unidadeBeneficiadaId());
		var mandato = registro.registrar(novo, UUID.fromString(jwt.getSubject()));
		return MandatoResposta.de(consulta.obter(mandato.getId()));
	}

	@Operation(summary = "Encerrar antes do prazo: renúncia, destituição... (síndico ou administradora)")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping("/{id}/encerramento")
	MandatoResposta encerrar(@PathVariable UUID id, @Valid @RequestBody PedidoEncerramento pedido) {
		registro.encerrar(id, pedido.ultimoDia(), pedido.motivo(), pedido.ataId());
		return MandatoResposta.de(consulta.obter(id));
	}

}
