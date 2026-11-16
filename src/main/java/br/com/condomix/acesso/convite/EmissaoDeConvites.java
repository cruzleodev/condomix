package br.com.condomix.acesso.convite;

import br.com.condomix.acesso.ConviteGerado;
import br.com.condomix.acesso.Perfil;
import br.com.condomix.acesso.Pessoa;
import br.com.condomix.acesso.persistencia.ConviteRepository;
import br.com.condomix.acesso.persistencia.PessoaRepository;
import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Caso de uso: convidar uma pessoa do condomínio atual para ter acesso. Um convite novo cancela o pendente. */
@Service
@Transactional
public class EmissaoDeConvites {

	private final ConviteRepository convites;

	private final PessoaRepository pessoas;

	private final Clock relogio;

	private final Duration validade;

	private final String enderecoDoApp;

	public EmissaoDeConvites(ConviteRepository convites, PessoaRepository pessoas, Clock relogio,
			@Value("${condomix.convite.validade:7d}") Duration validade,
			@Value("${condomix.convite.endereco-do-app:http://localhost:5173/convite}") String enderecoDoApp) {
		this.convites = convites;
		this.pessoas = pessoas;
		this.relogio = relogio;
		this.validade = validade;
		this.enderecoDoApp = enderecoDoApp;
	}

	/** @param perfil papel a receber; {@code null} quando o papel vem de um mandato (ex.: síndico) */
	public ConviteGerado convidar(UUID pessoaId, String email, Perfil perfil, UUID criadoPor) {
		Pessoa pessoa = pessoas.findById(pessoaId)
				.orElseThrow(() -> new NaoEncontradoException("Pessoa não encontrada neste condomínio"));
		if (pessoa.getUsuarioId() != null && perfil == null) {
			throw RegraDeNegocioException.conflito(pessoa.getNome() + " já tem acesso ao sistema");
		}
		Instant agora = Instant.now(relogio);
		convites.pendentesDaPessoa(pessoaId).forEach(c -> c.cancelar(agora));

		String codigo = CodigoDeConvite.gerar();
		Convite convite;
		try {
			convite = convites.save(new Convite(pessoa, email, perfil, CodigoDeConvite.impressaoDigital(codigo),
					agora.plus(validade), criadoPor));
		}
		catch (IllegalArgumentException e) {
			throw RegraDeNegocioException.invalido(e.getMessage());
		}
		if (pessoa.getEmail() == null) {
			pessoa.alterarContato(convite.getEmail(), pessoa.getTelefone());
		}
		return new ConviteGerado(convite.getId(), convite.getEmail(), enderecoDoApp + "?codigo=" + codigo,
				convite.getExpiraEm());
	}

}
