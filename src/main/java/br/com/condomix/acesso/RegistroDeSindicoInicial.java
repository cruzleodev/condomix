package br.com.condomix.acesso;

import br.com.condomix.acesso.convite.EmissaoDeConvites;
import br.com.condomix.acesso.mandato.RegistroDeMandatos;
import br.com.condomix.acesso.persistencia.PessoaRepository;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.compartilhado.TenantAtual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/** Pessoa, mandato e convite numa só transação, dentro do condomínio novo. */
@Service
class RegistroDeSindicoInicial implements IngressoDeSindico {

	private final PessoaRepository pessoas;

	private final RegistroDeMandatos mandatos;

	private final EmissaoDeConvites convites;

	private final TransactionTemplate transacao;

	RegistroDeSindicoInicial(PessoaRepository pessoas, RegistroDeMandatos mandatos, EmissaoDeConvites convites,
			TransactionTemplate transacao) {
		this.pessoas = pessoas;
		this.mandatos = mandatos;
		this.convites = convites;
		this.transacao = transacao;
	}

	@Override
	public ConviteGerado registrar(UUID condominioId, NovoSindico sindico, UUID registradoPor) {
		return TenantAtual.executarComo(condominioId, () -> transacao.execute(status -> {
			Pessoa pessoa = new Pessoa(condominioId, sindico.nome());
			try {
				pessoa.definirDocumento(sindico.documento());
				pessoa.alterarContato(sindico.email(), null);
			}
			catch (IllegalArgumentException e) {
				throw RegraDeNegocioException.invalido(e.getMessage());
			}
			pessoas.save(pessoa);
			mandatos.registrar(new RegistroDeMandatos.NovoMandato(pessoa.getId(), Cargo.SINDICO,
					sindico.inicioMandato(), sindico.fimMandato(), null, null, null), registradoPor);
			return convites.convidar(pessoa.getId(), sindico.email(), null, registradoPor);
		}));
	}

}
