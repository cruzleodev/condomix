package br.com.condomix.reservas.aplicacao;

import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.reservas.dominio.Espaco;
import br.com.condomix.reservas.dominio.Espaco.DadosDoEspaco;
import br.com.condomix.reservas.persistencia.EspacoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** A gestão cadastra os espaços comuns, com os horários, o aluguel e as regras. */
@Service
@Transactional
public class CadastroDeEspacos {

	private final EspacoRepository espacos;

	public CadastroDeEspacos(EspacoRepository espacos) {
		this.espacos = espacos;
	}

	public Espaco cadastrar(DadosDoEspaco dados) {
		if (dados.nome() != null && espacos.existsByNomeIgnoreCase(dados.nome().strip())) {
			throw RegraDeNegocioException.conflito("Já existe um espaço com este nome");
		}
		return espacos.save(regra(() -> new Espaco(TenantAtual.obrigatorio(), dados)));
	}

	public Espaco alterar(UUID id, DadosDoEspaco dados) {
		Espaco espaco = buscar(id);
		if (dados.nome() != null && espacos.existsByNomeIgnoreCaseAndIdNot(dados.nome().strip(), id)) {
			throw RegraDeNegocioException.conflito("Já existe um espaço com este nome");
		}
		regra(() -> {
			espaco.alterar(dados);
			return espaco;
		});
		return espaco;
	}

	public Espaco desativar(UUID id) {
		Espaco espaco = buscar(id);
		espaco.desativar();
		return espaco;
	}

	public Espaco reativar(UUID id) {
		Espaco espaco = buscar(id);
		espaco.reativar();
		return espaco;
	}

	@Transactional(readOnly = true)
	public List<Espaco> todos() {
		return espacos.findAllByOrderByNome();
	}

	@Transactional(readOnly = true)
	public Espaco buscar(UUID id) {
		return espacos.findById(id).orElseThrow(() -> new NaoEncontradoException("Espaço não encontrado"));
	}

	/** As recusas da entidade viram 400 com a explicação. */
	private static <T> T regra(Supplier<T> acao) {
		try {
			return acao.get();
		}
		catch (IllegalArgumentException | NullPointerException e) {
			throw RegraDeNegocioException.invalido(e.getMessage());
		}
	}

}
