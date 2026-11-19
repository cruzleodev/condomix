package br.com.condomix.reservas.web;

import br.com.condomix.acesso.CadastroParaReservas.UnidadeParaReserva;
import br.com.condomix.acesso.Permissoes;
import br.com.condomix.reservas.aplicacao.AgendaDeReservas;
import br.com.condomix.reservas.aplicacao.AgendaDeReservas.AluguelNoCancelamento;
import br.com.condomix.reservas.aplicacao.AgendaDeReservas.NovaReserva;
import br.com.condomix.reservas.aplicacao.AgendaDeReservas.Solicitante;
import br.com.condomix.reservas.aplicacao.ConsultaDeReservas;
import br.com.condomix.reservas.aplicacao.ConsultaDeReservas.ReservaDetalhada;
import br.com.condomix.reservas.dominio.SituacaoDaReserva;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Tag(name = "Reservas")
@RestController
@RequestMapping("/api")
class ReservaController {

	private final AgendaDeReservas agenda;

	private final ConsultaDeReservas consulta;

	private final Clock relogio;

	ReservaController(AgendaDeReservas agenda, ConsultaDeReservas consulta, Clock relogio) {
		this.agenda = agenda;
		this.consulta = consulta;
		this.relogio = relogio;
	}

	/** @param unidadeId a unidade que usa o espaço (o morador escolhe entre as suas) */
	record PedidoReserva(@NotNull UUID espacoId, @NotNull LocalDate data, @NotBlank String horario, @NotNull UUID unidadeId,
			@Size(max = 500) String observacao) {
	}

	/** @param dispensarAluguel só a gestão: cancela o aluguel mesmo fora do prazo */
	record PedidoCancelamentoDeReserva(@Size(max = 500) String motivo, Boolean dispensarAluguel) {
	}

	/**
	 * @param cancelavelSemCustoAte último dia para cancelar sem pagar o aluguel
	 * @param aconteceu             a data já passou
	 * @param aluguelMantido        cancelada fora do prazo: o aluguel continua devido
	 */
	record ReservaResposta(UUID id, UUID espacoId, String espaco, UUID unidadeId, String unidade, LocalDate data,
			String horario, LocalTime inicio, LocalTime fim, SituacaoDaReserva situacao, BigDecimal valor, UUID cobrancaId,
			String observacao, LocalDate cancelavelSemCustoAte, boolean aconteceu, Instant canceladaEm,
			String motivoCancelamento, boolean aluguelMantido) {

		static ReservaResposta de(ReservaDetalhada d) {
			var r = d.reserva();
			return new ReservaResposta(r.getId(), r.getEspacoId(), d.espaco(), r.getUnidadeId(), d.unidade(), r.getData(),
					r.getPeriodo(), r.getInicio(), r.getFim(), r.getSituacao(), r.getValor(), r.getCobrancaId(), r.getObservacao(),
					d.cancelavelSemCustoAte(), d.aconteceu(), r.getCanceladaEm(), r.getMotivoCancelamento(), r.isAluguelMantido());
		}

	}

	record CancelamentoDaReserva(UUID reservaId, AluguelNoCancelamento aluguel) {
	}

	@Operation(summary = "Unidades para as quais posso reservar",
			description = "A gestão: todas as ativas. O morador: as suas (proprietário, inquilino ou morador).")
	@GetMapping("/reservas/unidades")
	List<UnidadeParaReserva> unidades(@AuthenticationPrincipal Jwt jwt) {
		return agenda.unidadesPermitidas(solicitante(jwt), LocalDate.now(relogio));
	}

	@Operation(summary = "Reservar um horário de um espaço",
			description = "Vale na hora, se passar nas regras do espaço. Se o espaço cobra, o aluguel vira cobrança avulsa da unidade, "
					+ "vencendo no dia da reserva.")
	@PostMapping("/reservas")
	@ResponseStatus(HttpStatus.CREATED)
	ReservaResposta reservar(@Valid @RequestBody PedidoReserva p, @AuthenticationPrincipal Jwt jwt) {
		var reserva = agenda.reservar(new NovaReserva(p.espacoId(), p.data(), p.horario(), p.unidadeId(), p.observacao()),
				solicitante(jwt));
		return ReservaResposta.de(consulta.detalhada(reserva.getId()));
	}

	@Operation(summary = "Cancelar uma reserva",
			description = "Até o dia da reserva. No prazo do espaço, o aluguel é cancelado; depois dele, continua devido.")
	@PostMapping("/reservas/{id}/cancelamento")
	CancelamentoDaReserva cancelar(@PathVariable UUID id, @Valid @RequestBody(required = false) PedidoCancelamentoDeReserva p,
			@AuthenticationPrincipal Jwt jwt) {
		var resultado = agenda.cancelar(id, p == null ? null : p.motivo(), p != null && Boolean.TRUE.equals(p.dispensarAluguel()), solicitante(jwt));
		return new CancelamentoDaReserva(resultado.reserva().getId(), resultado.aluguel());
	}

	@Operation(summary = "Minhas reservas: as das minhas unidades, dos últimos 30 dias em diante")
	@GetMapping("/eu/reservas")
	List<ReservaResposta> minhas(@AuthenticationPrincipal Jwt jwt) {
		return consulta.minhas(UUID.fromString(jwt.getSubject())).stream().map(ReservaResposta::de).toList();
	}

	@Operation(summary = "Reservas do condomínio no período (gestão e conselho)")
	@PreAuthorize(Permissoes.CONSULTA_DO_CADASTRO)
	@GetMapping("/reservas")
	List<ReservaResposta> doCondominio(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
		return consulta.doCondominio(de, ate).stream().map(ReservaResposta::de).toList();
	}

	private static Solicitante solicitante(Jwt jwt) {
		return new Solicitante(UUID.fromString(jwt.getSubject()), EspacoController.ehGestao(jwt));
	}

}
