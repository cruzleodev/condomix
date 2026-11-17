package br.com.condomix.acesso.pessoa;

import br.com.condomix.acesso.ConviteGerado;
import br.com.condomix.acesso.Permissoes;
import br.com.condomix.acesso.Pessoa;
import br.com.condomix.acesso.TipoVinculo;
import br.com.condomix.acesso.VinculoUnidade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Pessoas e vínculos com unidades. Consultar: gestão, subsíndico e conselho. Cadastrar: síndico ou administradora. */
@Tag(name = "Pessoas")
@RestController
@RequestMapping("/api")
class PessoaController {

	private final CadastroDePessoas cadastro;

	private final ConsultaDePessoas consulta;

	private final CadastroDeMorador moradores;

	PessoaController(CadastroDePessoas cadastro, ConsultaDePessoas consulta, CadastroDeMorador moradores) {
		this.cadastro = cadastro;
		this.consulta = consulta;
		this.moradores = moradores;
	}

	record PedidoPessoa(@NotBlank @Size(max = 150) String nome, @Size(max = 20) String documento,
			@Email @Size(max = 150) String email, @Size(max = 25) String telefone) {

		CadastroDePessoas.DadosDaPessoa paraDados() {
			return new CadastroDePessoas.DadosDaPessoa(nome, documento, email, telefone);
		}

	}

	record PedidoVinculo(@NotNull UUID unidadeId, @NotNull TipoVinculo tipo, LocalDate inicio) {
	}

	record PedidoEncerramentoDeVinculo(@NotNull LocalDate fim) {
	}

	record PedidoMorador(@NotNull @Valid PedidoPessoa pessoa, @NotNull UUID unidadeId, @NotNull TipoVinculo vinculo,
			LocalDate inicio, Boolean convidar) {
	}

	record PessoaResposta(UUID id, String nome, String documento, String email, String telefone, boolean temLogin,
			List<UnidadeDaPessoa> unidades) {

		static PessoaResposta de(Pessoa p, List<VinculoUnidade> vinculos) {
			return new PessoaResposta(p.getId(), p.getNome(), p.getDocumento(), p.getEmail(), p.getTelefone(),
					p.getUsuarioId() != null, vinculos.stream().map(UnidadeDaPessoa::de).toList());
		}

	}

	record UnidadeDaPessoa(UUID vinculoId, UUID unidadeId, String unidade, TipoVinculo vinculo, LocalDate inicio,
			LocalDate fim) {

		static UnidadeDaPessoa de(VinculoUnidade v) {
			return new UnidadeDaPessoa(v.getId(), v.getUnidade().getId(), v.getUnidade().descricao(), v.getTipo(),
					v.getInicio(), v.getFim());
		}

	}

	record MoradorResposta(PessoaResposta pessoa, ConviteGerado convite) {
	}

	@Operation(summary = "Pessoas do condomínio e suas unidades atuais", description = "busca: parte do nome ou CPF/CNPJ.")
	@PreAuthorize(Permissoes.CONSULTA_DO_CADASTRO)
	@GetMapping("/pessoas")
	List<PessoaResposta> listar(@RequestParam(required = false) String busca) {
		return consulta.listar(busca).stream().map(p -> PessoaResposta.de(p.pessoa(), p.vinculos())).toList();
	}

	@Operation(summary = "Uma pessoa, com o histórico de vínculos")
	@PreAuthorize(Permissoes.CONSULTA_DO_CADASTRO)
	@GetMapping("/pessoas/{id}")
	PessoaResposta obter(@PathVariable UUID id) {
		var p = consulta.obter(id);
		return PessoaResposta.de(p.pessoa(), p.vinculos());
	}

	@Operation(summary = "Cadastrar pessoa", description = "documento (CPF ou CNPJ, inclusive alfanumérico) e telefone são opcionais e validados.")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping("/pessoas")
	@ResponseStatus(HttpStatus.CREATED)
	PessoaResposta criar(@Valid @RequestBody PedidoPessoa pedido) {
		return PessoaResposta.de(cadastro.criar(pedido.paraDados()), List.of());
	}

	@Operation(summary = "Corrigir dados de uma pessoa")
	@PreAuthorize(Permissoes.GESTAO)
	@PutMapping("/pessoas/{id}")
	PessoaResposta alterar(@PathVariable UUID id, @Valid @RequestBody PedidoPessoa pedido) {
		cadastro.alterar(id, pedido.paraDados());
		return obter(id);
	}

	@Operation(summary = "Ligar a pessoa a uma unidade", description = "tipo: PROPRIETARIO, INQUILINO, MORADOR ou DEPENDENTE. inicio vazio = hoje.")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping("/pessoas/{id}/vinculos")
	@ResponseStatus(HttpStatus.CREATED)
	PessoaResposta vincular(@PathVariable UUID id, @Valid @RequestBody PedidoVinculo pedido) {
		cadastro.vincular(id, pedido.unidadeId(), pedido.tipo(), pedido.inicio());
		return obter(id);
	}

	@Operation(summary = "Encerrar um vínculo (mudança, venda...)", description = "fim é o último dia. O histórico fica guardado.")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping("/pessoas/{id}/vinculos/{vinculoId}/encerramento")
	PessoaResposta encerrarVinculo(@PathVariable UUID id, @PathVariable UUID vinculoId,
			@Valid @RequestBody PedidoEncerramentoDeVinculo pedido) {
		cadastro.encerrarVinculo(id, vinculoId, pedido.fim());
		return obter(id);
	}

	@Operation(summary = "Cadastrar morador num passo só: pessoa, unidade e convite",
			description = "Com e-mail e convidar=true (padrão), a resposta traz o link do convite (papel MORADOR).")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping("/moradores")
	@ResponseStatus(HttpStatus.CREATED)
	MoradorResposta cadastrarMorador(@Valid @RequestBody PedidoMorador pedido, @AuthenticationPrincipal Jwt jwt) {
		var resultado = moradores.cadastrar(new CadastroDeMorador.NovoMorador(pedido.pessoa().paraDados(),
				pedido.unidadeId(), pedido.vinculo(), pedido.inicio(), !Boolean.FALSE.equals(pedido.convidar())),
				UUID.fromString(jwt.getSubject()));
		return new MoradorResposta(obter(resultado.pessoa().getId()), resultado.convite());
	}

	@Operation(summary = "Minhas unidades no condomínio atual", description = "Para qualquer pessoa logada: as unidades ligadas a ela.")
	@GetMapping("/eu/unidades")
	List<UnidadeDaPessoa> minhasUnidades(@AuthenticationPrincipal Jwt jwt) {
		return consulta.unidadesDoUsuario(UUID.fromString(jwt.getSubject())).stream().map(UnidadeDaPessoa::de).toList();
	}

}
