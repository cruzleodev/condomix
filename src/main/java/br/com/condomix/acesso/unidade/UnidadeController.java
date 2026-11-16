package br.com.condomix.acesso.unidade;

import br.com.condomix.acesso.Permissoes;
import br.com.condomix.acesso.TipoUnidade;
import br.com.condomix.acesso.TipoVinculo;
import br.com.condomix.acesso.Unidade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
import java.util.List;
import java.util.UUID;

/** Unidades do condomínio. Consultar: gestão, subsíndico e conselho. Cadastrar e alterar: síndico ou administradora. */
@Tag(name = "Unidades")
@RestController
@RequestMapping("/api/unidades")
class UnidadeController {

	private final CadastroDeUnidades cadastro;

	private final ConsultaDeUnidades consulta;

	UnidadeController(CadastroDeUnidades cadastro, ConsultaDeUnidades consulta) {
		this.cadastro = cadastro;
		this.consulta = consulta;
	}

	record PedidoUnidade(@Size(max = 30) String bloco, @NotBlank @Size(max = 30) String identificacao,
			@NotNull TipoUnidade tipo, BigDecimal fracaoIdeal) {

		CadastroDeUnidades.DadosDaUnidade paraDados() {
			return new CadastroDeUnidades.DadosDaUnidade(bloco, identificacao, tipo, fracaoIdeal);
		}

	}

	record PedidoLote(@Size(max = 30) String bloco, @NotNull TipoUnidade tipo,
			@NotEmpty @Size(max = CadastroDeUnidades.MAXIMO_POR_LOTE) List<@NotBlank @Size(max = 30) String> identificacoes) {
	}

	record UnidadeResposta(UUID id, String bloco, String identificacao, String descricao, TipoUnidade tipo,
			BigDecimal fracaoIdeal, boolean ativo, List<Ocupante> ocupantes) {

		record Ocupante(UUID vinculoId, UUID pessoaId, String nome, TipoVinculo vinculo) {
		}

		static UnidadeResposta de(ConsultaDeUnidades.UnidadeComOcupantes u) {
			return de(u.unidade(), u.vinculos().stream()
					.map(v -> new Ocupante(v.getId(), v.getPessoa().getId(), v.getPessoa().getNome(), v.getTipo()))
					.toList());
		}

		static UnidadeResposta de(Unidade u, List<Ocupante> ocupantes) {
			return new UnidadeResposta(u.getId(), u.getBloco(), u.getIdentificacao(), u.descricao(), u.getTipo(),
					u.getFracaoIdeal(), u.isAtivo(), ocupantes);
		}

	}

	@Operation(summary = "Unidades com os ocupantes atuais", description = "Filtre por bloco; inativas só com incluirInativas=true.")
	@PreAuthorize(Permissoes.CONSULTA_DO_CADASTRO)
	@GetMapping
	List<UnidadeResposta> listar(@RequestParam(required = false) String bloco,
			@RequestParam(defaultValue = "false") boolean incluirInativas) {
		return consulta.listar(bloco, incluirInativas).stream().map(UnidadeResposta::de).toList();
	}

	@Operation(summary = "Resumo: quantidade, blocos e se as frações ideais somam 100%")
	@PreAuthorize(Permissoes.CONSULTA_DO_CADASTRO)
	@GetMapping("/resumo")
	ConsultaDeUnidades.Resumo resumo() {
		return consulta.resumo();
	}

	@Operation(summary = "Uma unidade")
	@PreAuthorize(Permissoes.CONSULTA_DO_CADASTRO)
	@GetMapping("/{id}")
	UnidadeResposta obter(@PathVariable UUID id) {
		return UnidadeResposta.de(consulta.obter(id));
	}

	@Operation(summary = "Cadastrar unidade", description = "fracaoIdeal entre 0 e 1 (ex.: 0.025 = 2,5%); opcional.")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	UnidadeResposta criar(@Valid @RequestBody PedidoUnidade pedido) {
		return UnidadeResposta.de(cadastro.criar(pedido.paraDados()), List.of());
	}

	@Operation(summary = "Cadastrar várias unidades de uma vez (tudo ou nada)",
			description = "Ex.: {\"bloco\": \"A\", \"tipo\": \"APARTAMENTO\", \"identificacoes\": [\"101\", \"102\", \"201\", \"202\"]}")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping("/lote")
	@ResponseStatus(HttpStatus.CREATED)
	List<UnidadeResposta> criarLote(@Valid @RequestBody PedidoLote pedido) {
		return cadastro.criarLote(pedido.bloco(), pedido.tipo(), pedido.identificacoes()).stream()
				.map(u -> UnidadeResposta.de(u, List.of())).toList();
	}

	@Operation(summary = "Corrigir uma unidade")
	@PreAuthorize(Permissoes.GESTAO)
	@PutMapping("/{id}")
	UnidadeResposta alterar(@PathVariable UUID id, @Valid @RequestBody PedidoUnidade pedido) {
		cadastro.alterar(id, pedido.paraDados());
		return UnidadeResposta.de(consulta.obter(id));
	}

	@Operation(summary = "Desativar unidade (só sem pessoas vinculadas)")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping("/{id}/desativacao")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void desativar(@PathVariable UUID id) {
		cadastro.desativar(id);
	}

	@Operation(summary = "Reativar unidade")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping("/{id}/reativacao")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void reativar(@PathVariable UUID id) {
		cadastro.reativar(id);
	}

}
