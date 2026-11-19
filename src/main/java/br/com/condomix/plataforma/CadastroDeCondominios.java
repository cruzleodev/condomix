package br.com.condomix.plataforma;

import java.util.UUID;

/** Entrada de um novo cliente no Condomix: cria o condomínio e a assinatura em período de teste. */
public interface CadastroDeCondominios {

	/**
	 * @param cnpj        só dígitos; opcional
	 * @param codigoPlano código de um plano BASE, ex.: "ESSENCIAL" ou "COMPLETO"
	 */
	record NovoCondominio(String nome, TipoCondominio tipo, String cnpj, String codigoPlano, Periodicidade periodicidade) {
	}

	/**
	 * @param registradoPor quem cadastrou (vai para o histórico da assinatura); vazio quando é o sistema
	 * @return o id do condomínio, que é o tenant_id dos dados dele
	 */
	UUID cadastrar(NovoCondominio novo, UUID registradoPor);

	default UUID cadastrar(NovoCondominio novo) {
		return cadastrar(novo, null);
	}

	default UUID cadastrar(String nome, TipoCondominio tipo, String codigoPlano, Periodicidade periodicidade) {
		return cadastrar(new NovoCondominio(nome, tipo, null, codigoPlano, periodicidade));
	}

}
