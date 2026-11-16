package br.com.condomix.acesso.pessoa;

import br.com.condomix.acesso.Pessoa;
import br.com.condomix.acesso.VinculoUnidade;
import br.com.condomix.acesso.persistencia.PessoaRepository;
import br.com.condomix.acesso.persistencia.VinculoUnidadeRepository;
import br.com.condomix.compartilhado.NaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Caso de uso: consultar as pessoas do condomínio e as unidades de cada uma. */
@Service
@Transactional(readOnly = true)
public class ConsultaDePessoas {

	private final PessoaRepository pessoas;

	private final VinculoUnidadeRepository vinculos;

	private final Clock relogio;

	public ConsultaDePessoas(PessoaRepository pessoas, VinculoUnidadeRepository vinculos, Clock relogio) {
		this.pessoas = pessoas;
		this.vinculos = vinculos;
		this.relogio = relogio;
	}

	/** @param vinculos na lista: só os atuais; no detalhe: o histórico completo */
	public record PessoaComVinculos(Pessoa pessoa, List<VinculoUnidade> vinculos) {
	}

	public List<PessoaComVinculos> listar(String busca) {
		Map<UUID, List<VinculoUnidade>> atuais = vinculos.vigentes(LocalDate.now(relogio)).stream()
				.collect(Collectors.groupingBy(v -> v.getPessoa().getId()));
		List<Pessoa> lista = busca == null || busca.isBlank() ? pessoas.todasEmOrdem() : pessoas.buscar(busca.strip());
		return lista.stream().map(p -> new PessoaComVinculos(p, atuais.getOrDefault(p.getId(), List.of()))).toList();
	}

	public PessoaComVinculos obter(UUID id) {
		Pessoa pessoa = pessoas.findById(id).orElseThrow(() -> new NaoEncontradoException("Pessoa não encontrada"));
		return new PessoaComVinculos(pessoa, vinculos.historicoDaPessoa(id));
	}

	/** "Meus dados": as unidades do usuário logado no condomínio atual. */
	public List<VinculoUnidade> unidadesDoUsuario(UUID usuarioId) {
		return vinculos.vigentesDoUsuario(usuarioId, LocalDate.now(relogio));
	}

}
