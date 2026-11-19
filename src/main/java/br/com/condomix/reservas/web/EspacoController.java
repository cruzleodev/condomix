package br.com.condomix.reservas.web;

import br.com.condomix.acesso.Permissoes;
import br.com.condomix.reservas.aplicacao.AgendaDeReservas.Solicitante;
import br.com.condomix.reservas.aplicacao.CadastroDeEspacos;
import br.com.condomix.reservas.aplicacao.ConsultaDeReservas;
import br.com.condomix.reservas.aplicacao.ConsultaDeReservas.DiaNaAgenda;
import br.com.condomix.reservas.dominio.Espaco;
import br.com.condomix.reservas.dominio.PeriodoDoEspaco;
import br.com.condomix.reservas.dominio.RegrasDoEspaco;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Tag(name = "Reservas: espaços")
@RestController
@RequestMapping("/api/espacos")
class EspacoController {

	private final CadastroDeEspacos cadastro;

	private final ConsultaDeReservas consulta;

	EspacoController(CadastroDeEspacos cadastro, ConsultaDeReservas consulta) {
		this.cadastro = cadastro;
		this.consulta = consulta;
	}

	record PedidoHorario(@NotBlank @Size(max = 50) String nome, @NotNull LocalTime inicio, @NotNull LocalTime fim) {
	}

	/**
	 * Vazios valem o padrão: antecedência de 0 a 90 dias, sem limite por mês, bloqueia inadimplente, cancela até o dia.
	 *
	 * @param limitePorMes     vazio: sem limite
	 * @param diasParaCancelar cancelar até tantos dias antes não tem custo (0: até o próprio dia)
	 */
	record PedidoEspaco(@NotBlank @Size(max = 100) String nome, @Size(max = 2000) String descricao, Integer capacidade,
			@DecimalMin("0") BigDecimal valorAluguel, Integer antecedenciaMinimaDias, Integer antecedenciaMaximaDias,
			Integer limitePorMes, Boolean bloqueiaInadimplente, Integer diasParaCancelar,
			@NotEmpty List<@Valid PedidoHorario> horarios) {

		Espaco.DadosDoEspaco paraDominio() {
			RegrasDoEspaco regras;
			try {
				var padrao = RegrasDoEspaco.padrao();
				regras = new RegrasDoEspaco(
						antecedenciaMinimaDias == null ? padrao.antecedenciaMinimaDias() : antecedenciaMinimaDias,
						antecedenciaMaximaDias == null ? padrao.antecedenciaMaximaDias() : antecedenciaMaximaDias, limitePorMes,
						bloqueiaInadimplente == null ? padrao.bloqueiaInadimplente() : bloqueiaInadimplente,
						diasParaCancelar == null ? padrao.diasParaCancelar() : diasParaCancelar);
			}
			catch (IllegalArgumentException e) {
				throw br.com.condomix.compartilhado.RegraDeNegocioException.invalido(e.getMessage());
			}
			List<PeriodoDoEspaco> periodos;
			try {
				periodos = horarios.stream().map(h -> new PeriodoDoEspaco(h.nome(), h.inicio(), h.fim())).toList();
			}
			catch (IllegalArgumentException | NullPointerException e) {
				throw br.com.condomix.compartilhado.RegraDeNegocioException.invalido(e.getMessage());
			}
			return new Espaco.DadosDoEspaco(nome, descricao, capacidade, valorAluguel, regras, periodos);
		}

	}

	record HorarioDoEspaco(String nome, LocalTime inicio, LocalTime fim) {
	}

	/** @param limitePorMes vazio: sem limite */
	record RegrasDeReserva(int antecedenciaMinimaDias, int antecedenciaMaximaDias, Integer limitePorMes,
			boolean bloqueiaInadimplente, int diasParaCancelar) {
	}

	record EspacoResposta(UUID id, String nome, String descricao, Integer capacidade, BigDecimal valorAluguel,
			RegrasDeReserva regras, List<HorarioDoEspaco> horarios, boolean ativo) {

		static EspacoResposta de(Espaco e) {
			RegrasDoEspaco r = e.getRegras();
			return new EspacoResposta(e.getId(), e.getNome(), e.getDescricao(), e.getCapacidade(), e.getValorAluguel(),
					new RegrasDeReserva(r.antecedenciaMinimaDias(), r.antecedenciaMaximaDias(), r.limitePorMes(),
							r.bloqueiaInadimplente(), r.diasParaCancelar()),
					e.getPeriodos().stream().map(p -> new HorarioDoEspaco(p.nome(), p.inicio(), p.fim())).toList(), e.isAtivo());
		}

	}

	@Operation(summary = "Espaços comuns", description = "A gestão vê também os desativados.")
	@GetMapping
	List<EspacoResposta> listar(@AuthenticationPrincipal Jwt jwt) {
		boolean gestao = ehGestao(jwt);
		return cadastro.todos().stream().filter(e -> gestao || e.isAtivo()).map(EspacoResposta::de).toList();
	}

	@Operation(summary = "Um espaço")
	@GetMapping("/{id}")
	EspacoResposta obter(@PathVariable UUID id) {
		return EspacoResposta.de(cadastro.buscar(id));
	}

	@Operation(summary = "Cadastrar um espaço comum, com os horários, o aluguel e as regras")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	EspacoResposta cadastrar(@Valid @RequestBody PedidoEspaco pedido) {
		return EspacoResposta.de(cadastro.cadastrar(pedido.paraDominio()));
	}

	@Operation(summary = "Alterar o espaço (as reservas já feitas guardam o horário e o valor de quando foram feitas)")
	@PreAuthorize(Permissoes.GESTAO)
	@PutMapping("/{id}")
	EspacoResposta alterar(@PathVariable UUID id, @Valid @RequestBody PedidoEspaco pedido) {
		return EspacoResposta.de(cadastro.alterar(id, pedido.paraDominio()));
	}

	@Operation(summary = "Desativar: o espaço para de aceitar reservas (as feitas continuam valendo)")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping("/{id}/desativacao")
	EspacoResposta desativar(@PathVariable UUID id) {
		return EspacoResposta.de(cadastro.desativar(id));
	}

	@Operation(summary = "Reativar o espaço")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping("/{id}/reativacao")
	EspacoResposta reativar(@PathVariable UUID id) {
		return EspacoResposta.de(cadastro.reativar(id));
	}

	@Operation(summary = "Agenda do espaço: cada dia, cada horário livre, ocupado ou da minha unidade",
			description = "Até 62 dias de cada vez. Só a gestão vê qual unidade reservou.")
	@GetMapping("/{id}/agenda")
	List<DiaNaAgenda> agenda(@PathVariable UUID id,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate, @AuthenticationPrincipal Jwt jwt) {
		return consulta.agenda(id, de, ate, new Solicitante(UUID.fromString(jwt.getSubject()), ehGestao(jwt)));
	}

	static boolean ehGestao(Jwt jwt) {
		List<String> perfis = jwt.getClaimAsStringList("perfis");
		return perfis != null && (perfis.contains("SINDICO") || perfis.contains("ADMINISTRADORA"));
	}

}
