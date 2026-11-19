package br.com.condomix.administracao;

import br.com.condomix.acesso.IngressoDeSindico;
import br.com.condomix.plataforma.CadastroDeCondominios;
import br.com.condomix.plataforma.PainelDeCondominios;
import br.com.condomix.plataforma.PainelDeCondominios.CondominioNaPlataforma;
import br.com.condomix.plataforma.Periodicidade;
import br.com.condomix.plataforma.StatusAssinatura;
import br.com.condomix.plataforma.TipoCondominio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
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
import java.util.List;
import java.util.UUID;

@Tag(name = "Plataforma: condomínios")
@RestController
@RequestMapping("/api/plataforma/condominios")
class CondominiosDaPlataformaController {

	private final PainelDeCondominios painel;

	private final IngressoDeCliente ingresso;

	private final HistoricoDaAssinatura historico;

	CondominiosDaPlataformaController(PainelDeCondominios painel, IngressoDeCliente ingresso,
			HistoricoDaAssinatura historico) {
		this.painel = painel;
		this.ingresso = ingresso;
		this.historico = historico;
	}

	record PedidoClienteNovo(
			@NotBlank @Size(max = 150) String nome,
			@NotNull TipoCondominio tipo,
			@Size(max = 20) String cnpj,
			@NotBlank String plano,
			@NotNull Periodicidade periodicidade,
			@NotNull @Valid Sindico sindico) {

		record Sindico(@NotBlank @Size(max = 150) String nome, @NotBlank @Email String email, String documento,
				@NotNull LocalDate inicioMandato, @NotNull LocalDate fimMandato) {
		}

	}

	/** Vazio = teste sem prazo. */
	record PedidoFimDoTeste(LocalDate fimDoTeste) {
	}

	record PedidoDadosDoCliente(@Size(max = 20) String cnpj, @Email @Size(max = 150) String email,
			@Size(max = 20) String telefone) {
	}

	record PedidoAssinatura(@NotBlank String plano, @NotNull Periodicidade periodicidade, @NotNull StatusAssinatura status) {
	}

	@Operation(summary = "Clientes: todos os condomínios, com plano e situação da assinatura")
	@GetMapping
	List<CondominioNaPlataforma> listar() {
		return painel.listar();
	}

	@Operation(summary = "Um condomínio")
	@GetMapping("/{id}")
	CondominioNaPlataforma obter(@PathVariable UUID id) {
		return painel.obter(id);
	}

	@Operation(summary = "Cliente novo: condomínio, assinatura em teste, síndico e convite",
			description = "plano: ESSENCIAL ou COMPLETO. A resposta traz o link do convite para o síndico criar o login (vale 7 dias).")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	IngressoDeCliente.ClienteNovo registrar(@Valid @RequestBody PedidoClienteNovo pedido, @AuthenticationPrincipal Jwt jwt) {
		var s = pedido.sindico();
		return ingresso.registrar(
				new CadastroDeCondominios.NovoCondominio(pedido.nome(), pedido.tipo(), pedido.cnpj(), pedido.plano(),
						pedido.periodicidade()),
				new IngressoDeSindico.NovoSindico(s.nome(), s.email(), s.documento(), s.inicioMandato(), s.fimMandato()),
				UUID.fromString(jwt.getSubject()));
	}

	@Operation(summary = "Suspender: ninguém do condomínio consegue mais entrar")
	@PostMapping("/{id}/suspensao")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void suspender(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
		painel.suspender(id, UUID.fromString(jwt.getSubject()));
	}

	@Operation(summary = "Reativar um condomínio suspenso")
	@PostMapping("/{id}/reativacao")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void reativar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
		painel.reativar(id, UUID.fromString(jwt.getSubject()));
	}

	@Operation(summary = "Trocar plano, periodicidade ou situação da assinatura",
			description = "status: TESTE, ATIVA ou SUSPENSA. Trocar plano ou periodicidade guarda a assinatura anterior no histórico.")
	@PutMapping("/{id}/assinatura")
	CondominioNaPlataforma alterarAssinatura(@PathVariable UUID id, @Valid @RequestBody PedidoAssinatura pedido,
			@AuthenticationPrincipal Jwt jwt) {
		return painel.alterarAssinatura(id, pedido.plano(), pedido.periodicidade(), pedido.status(),
				UUID.fromString(jwt.getSubject()));
	}

	@Operation(summary = "Mudar o fim do teste grátis",
			description = "Só com a assinatura em teste. Vazio: teste sem prazo. Ao fim, a assinatura vira ativa e começa a ser faturada.")
	@PutMapping("/{id}/fim-do-teste")
	CondominioNaPlataforma definirFimDoTeste(@PathVariable UUID id, @RequestBody PedidoFimDoTeste pedido,
			@AuthenticationPrincipal Jwt jwt) {
		return painel.definirFimDoTeste(id, pedido.fimDoTeste(), UUID.fromString(jwt.getSubject()));
	}

	@Operation(summary = "Dados para a fatura: CNPJ, e-mail e telefone do condomínio")
	@PutMapping("/{id}/dados")
	CondominioNaPlataforma alterarDados(@PathVariable UUID id, @Valid @RequestBody PedidoDadosDoCliente pedido) {
		return painel.alterarDados(id, new PainelDeCondominios.DadosDoCliente(pedido.cnpj(), pedido.email(), pedido.telefone()));
	}

	@Operation(summary = "Histórico da assinatura: cada mudança de plano, de situação e de acesso, com quem fez")
	@GetMapping("/{id}/historico")
	List<HistoricoDaAssinatura.AlteracaoDaAssinatura> historico(@PathVariable UUID id) {
		return historico.doCondominio(id);
	}

}
