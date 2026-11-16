package br.com.condomix.plataforma;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/** Os planos vendidos e seus preços, mantidos pelo administrador da plataforma. */
public interface CatalogoDePlanos {

	List<PlanoNoCatalogo> listar();

	PlanoNoCatalogo definirPrecos(String codigoPlano, BigDecimal mensal, BigDecimal semestral, BigDecimal anual);

	record PlanoNoCatalogo(String codigo, String nome, String descricao, TipoPlano tipo, boolean ativo,
			Set<Modulo> modulos, BigDecimal precoMensal, BigDecimal precoSemestral, BigDecimal precoAnual) {
	}

}
