package br.com.condomix.plataforma;

import java.util.UUID;

/** Entrada de um novo cliente no Condomix: cria o condomínio e a assinatura em período de teste. */
public interface CadastroDeCondominios {

	/**
	 * @param codigoPlano código de um plano BASE, ex.: "ESSENCIAL" ou "COMPLETO"
	 * @return o id do condomínio, que é o tenant_id dos dados dele
	 */
	UUID cadastrar(String nome, TipoCondominio tipo, String codigoPlano, Periodicidade periodicidade);

}
