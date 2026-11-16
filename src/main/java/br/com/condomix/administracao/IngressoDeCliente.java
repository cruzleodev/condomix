package br.com.condomix.administracao;

import br.com.condomix.acesso.ConviteGerado;
import br.com.condomix.acesso.IngressoDeSindico;
import br.com.condomix.compartilhado.DocumentoBrasileiro;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.plataforma.CadastroDeCondominios;
import br.com.condomix.plataforma.PainelDeCondominios;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

/**
 * Caso de uso: um cliente novo entra no Condomix. Cria o condomínio (com assinatura em teste),
 * registra o primeiro síndico e gera o convite para ele criar o login.
 */
@Service
class IngressoDeCliente {

	/** Mesmo limite do mandato (Código Civil, art. 1.347), conferido antes de criar o condomínio. */
	private static final Period MANDATO_MAXIMO = Period.ofYears(2);

	private final CadastroDeCondominios cadastro;

	private final IngressoDeSindico sindicos;

	private final PainelDeCondominios painel;

	IngressoDeCliente(CadastroDeCondominios cadastro, IngressoDeSindico sindicos, PainelDeCondominios painel) {
		this.cadastro = cadastro;
		this.sindicos = sindicos;
		this.painel = painel;
	}

	record ClienteNovo(PainelDeCondominios.CondominioNaPlataforma condominio, ConviteGerado conviteDoSindico) {
	}

	/**
	 * Os dados são conferidos antes de criar qualquer coisa, para não deixar um condomínio sem síndico.
	 * (O condomínio e o síndico ficam em módulos diferentes, com transações separadas.)
	 */
	ClienteNovo registrar(CadastroDeCondominios.NovoCondominio condominio, IngressoDeSindico.NovoSindico sindico,
			UUID administradorId) {
		conferirMandato(sindico.inicioMandato(), sindico.fimMandato());
		try {
			DocumentoBrasileiro.normalizar(condominio.cnpj());
			DocumentoBrasileiro.normalizar(sindico.documento());
		}
		catch (IllegalArgumentException e) {
			throw RegraDeNegocioException.invalido(e.getMessage());
		}
		UUID condominioId;
		try {
			condominioId = cadastro.cadastrar(condominio);
		}
		catch (IllegalArgumentException e) {
			throw RegraDeNegocioException.invalido(e.getMessage());
		}
		ConviteGerado convite = sindicos.registrar(condominioId, sindico, administradorId);
		return new ClienteNovo(painel.obter(condominioId), convite);
	}

	private static void conferirMandato(LocalDate inicio, LocalDate fim) {
		if (fim.isBefore(inicio) || fim.isAfter(inicio.plus(MANDATO_MAXIMO).minusDays(1))) {
			throw RegraDeNegocioException.invalido("O mandato do síndico deve terminar depois da posse e durar no máximo 2 anos");
		}
	}

}
