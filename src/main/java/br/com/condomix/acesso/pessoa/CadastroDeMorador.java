package br.com.condomix.acesso.pessoa;

import br.com.condomix.acesso.ConviteGerado;
import br.com.condomix.acesso.Perfil;
import br.com.condomix.acesso.Pessoa;
import br.com.condomix.acesso.TipoVinculo;
import br.com.condomix.acesso.convite.EmissaoDeConvites;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Caso de uso do dia a dia do síndico: cadastrar um morador de uma vez. Cria a pessoa, liga à
 * unidade e, se houver e-mail, gera o convite de acesso (papel MORADOR). Tudo ou nada.
 */
@Service
@Transactional
public class CadastroDeMorador {

	private final CadastroDePessoas pessoas;

	private final EmissaoDeConvites convites;

	public CadastroDeMorador(CadastroDePessoas pessoas, EmissaoDeConvites convites) {
		this.pessoas = pessoas;
		this.convites = convites;
	}

	public record NovoMorador(CadastroDePessoas.DadosDaPessoa pessoa, UUID unidadeId, TipoVinculo vinculo,
			LocalDate inicio, boolean convidar) {
	}

	/** @param convite vazio quando não foi pedido ou não há e-mail */
	public record MoradorCadastrado(Pessoa pessoa, ConviteGerado convite) {
	}

	public MoradorCadastrado cadastrar(NovoMorador novo, UUID cadastradoPor) {
		Pessoa pessoa = pessoas.criar(novo.pessoa());
		pessoas.vincular(pessoa.getId(), novo.unidadeId(), novo.vinculo(), novo.inicio());
		ConviteGerado convite = novo.convidar() && pessoa.getEmail() != null
				? convites.convidar(pessoa.getId(), pessoa.getEmail(), Perfil.MORADOR, cadastradoPor)
				: null;
		return new MoradorCadastrado(pessoa, convite);
	}

}
