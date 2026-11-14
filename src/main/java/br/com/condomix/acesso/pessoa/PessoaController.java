package br.com.condomix.acesso.pessoa;

import br.com.condomix.acesso.TipoVinculo;
import br.com.condomix.acesso.persistencia.PessoaRepository;
import br.com.condomix.acesso.persistencia.VinculoUnidadeRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lista as pessoas do condomínio, para o síndico escolher quem ocupa um cargo.
 * Leitura simples, sem regra de negócio: fica no controller (os "10%" do CLAUDE.md).
 * O cadastro de pessoas e os convites vêm na etapa de administração.
 */
@Tag(name = "Pessoas")
@RestController
@RequestMapping("/api/pessoas")
class PessoaController {

	private final PessoaRepository pessoas;

	private final VinculoUnidadeRepository vinculos;

	private final Clock relogio;

	PessoaController(PessoaRepository pessoas, VinculoUnidadeRepository vinculos, Clock relogio) {
		this.pessoas = pessoas;
		this.vinculos = vinculos;
		this.relogio = relogio;
	}

	record PessoaResposta(UUID id, String nome, boolean temLogin, List<UnidadeDaPessoa> unidades) {
	}

	record UnidadeDaPessoa(UUID unidadeId, String bloco, String identificacao, TipoVinculo vinculo) {
	}

	@Operation(summary = "Pessoas do condomínio e suas unidades (síndico, subsíndico ou administradora)")
	@PreAuthorize("hasAnyRole('SINDICO', 'SUBSINDICO', 'ADMINISTRADORA')")
	@Transactional(readOnly = true)
	@GetMapping
	List<PessoaResposta> listar() {
		LocalDate hoje = LocalDate.now(relogio);
		var unidadesPorPessoa = vinculos.vigentes(hoje).stream().collect(Collectors.groupingBy(
				v -> v.getPessoa().getId(),
				Collectors.mapping(v -> new UnidadeDaPessoa(v.getUnidade().getId(), v.getUnidade().getBloco(),
						v.getUnidade().getIdentificacao(), v.getTipo()), Collectors.toList())));
		return pessoas.findAll().stream()
				.map(p -> new PessoaResposta(p.getId(), p.getNome(), p.getUsuarioId() != null,
						unidadesPorPessoa.getOrDefault(p.getId(), List.of())))
				.sorted((a, b) -> a.nome().compareToIgnoreCase(b.nome()))
				.toList();
	}

}
