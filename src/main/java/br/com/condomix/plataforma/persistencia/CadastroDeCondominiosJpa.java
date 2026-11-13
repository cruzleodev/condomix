package br.com.condomix.plataforma.persistencia;

import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.plataforma.Assinatura;
import br.com.condomix.plataforma.CadastroDeCondominios;
import br.com.condomix.plataforma.Condominio;
import br.com.condomix.plataforma.Periodicidade;
import br.com.condomix.plataforma.Plano;
import br.com.condomix.plataforma.StatusAssinatura;
import br.com.condomix.plataforma.TipoCondominio;
import br.com.condomix.plataforma.TipoPlano;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

@Component
class CadastroDeCondominiosJpa implements CadastroDeCondominios {

	private final CondominioRepository condominios;

	private final PlanoRepository planos;

	private final AssinaturaRepository assinaturas;

	private final Clock relogio;

	CadastroDeCondominiosJpa(CondominioRepository condominios, PlanoRepository planos,
			AssinaturaRepository assinaturas, Clock relogio) {
		this.condominios = condominios;
		this.planos = planos;
		this.assinaturas = assinaturas;
		this.relogio = relogio;
	}

	/**
	 * Duas transações em sequência: o condomínio precisa existir (e ter id) antes de a assinatura
	 * ser gravada dentro dele. Se a segunda falhar, o condomínio fica sem assinatura e pode receber
	 * uma depois; quando houver cadastro pela API (etapa de cobrança), isso vira uma única operação.
	 */
	@Override
	public UUID cadastrar(String nome, TipoCondominio tipo, String codigoPlano, Periodicidade periodicidade) {
		Plano plano = planos.findByCodigo(codigoPlano)
				.filter(p -> p.isAtivo() && p.getTipo() == TipoPlano.BASE)
				.orElseThrow(() -> new IllegalArgumentException("Plano base inexistente: " + codigoPlano));
		Condominio condominio = condominios.save(new Condominio(nome, tipo));
		TenantAtual.executarComo(condominio.getId(), () -> assinaturas.save(
				new Assinatura(condominio, plano, periodicidade, StatusAssinatura.TESTE, LocalDate.now(relogio))));
		return condominio.getId();
	}

}
