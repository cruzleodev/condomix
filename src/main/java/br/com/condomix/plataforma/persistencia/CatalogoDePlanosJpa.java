package br.com.condomix.plataforma.persistencia;

import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.plataforma.CatalogoDePlanos;
import br.com.condomix.plataforma.Plano;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Component
@Transactional
class CatalogoDePlanosJpa implements CatalogoDePlanos {

	private final PlanoRepository planos;

	CatalogoDePlanosJpa(PlanoRepository planos) {
		this.planos = planos;
	}

	@Override
	@Transactional(readOnly = true)
	public List<PlanoNoCatalogo> listar() {
		return planos.findAll().stream()
				.sorted(Comparator.comparing(Plano::getTipo).thenComparing(Plano::getCodigo))
				.map(CatalogoDePlanosJpa::paraCatalogo)
				.toList();
	}

	@Override
	public PlanoNoCatalogo definirPrecos(String codigoPlano, BigDecimal mensal, BigDecimal semestral, BigDecimal anual) {
		Plano plano = planos.findByCodigo(codigoPlano).orElseThrow(() -> new NaoEncontradoException("Plano não encontrado"));
		try {
			plano.definirPrecos(mensal, semestral, anual);
		}
		catch (IllegalArgumentException e) {
			throw RegraDeNegocioException.invalido(e.getMessage());
		}
		return paraCatalogo(plano);
	}

	private static PlanoNoCatalogo paraCatalogo(Plano p) {
		return new PlanoNoCatalogo(p.getCodigo(), p.getNome(), p.getDescricao(), p.getTipo(), p.isAtivo(),
				p.getModulos(), p.getPrecoMensal(), p.getPrecoSemestral(), p.getPrecoAnual());
	}

}
