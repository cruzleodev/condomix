package br.com.condomix.administracao;

import br.com.condomix.acesso.ConviteGerado;
import br.com.condomix.acesso.IngressoDeSindico;
import br.com.condomix.compartilhado.DocumentoBrasileiro;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.plataforma.CadastroDeCondominios;
import br.com.condomix.plataforma.PainelDeCondominios;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Caso de uso: um cliente novo entra no Condomix. Cria o condomínio (com assinatura em teste),
 * registra o primeiro síndico e gera o convite para ele criar o login.
 */
@Service
class IngressoDeCliente {

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
		SindicoDoCliente.conferirMandato(sindico.inicioMandato(), sindico.fimMandato());
		try {
			DocumentoBrasileiro.normalizar(condominio.cnpj());
			DocumentoBrasileiro.normalizar(sindico.documento());
		}
		catch (IllegalArgumentException e) {
			throw RegraDeNegocioException.invalido(e.getMessage());
		}
		UUID condominioId;
		try {
			condominioId = cadastro.cadastrar(condominio, administradorId);
		}
		catch (IllegalArgumentException e) {
			throw RegraDeNegocioException.invalido(e.getMessage());
		}
		ConviteGerado convite = sindicos.registrar(condominioId, sindico, administradorId).convite();
		return new ClienteNovo(painel.obter(condominioId), convite);
	}

}
