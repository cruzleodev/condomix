package br.com.condomix.plataforma.persistencia;

import br.com.condomix.compartilhado.DocumentoBrasileiro;
import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.plataforma.Assinatura;
import br.com.condomix.plataforma.CadastroDeCondominios;
import br.com.condomix.plataforma.Condominio;
import br.com.condomix.plataforma.Plano;
import br.com.condomix.plataforma.StatusAssinatura;
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
	public UUID cadastrar(NovoCondominio novo) {
		Plano plano = planos.findByCodigo(novo.codigoPlano())
				.filter(p -> p.isAtivo() && p.getTipo() == TipoPlano.BASE)
				.orElseThrow(() -> new IllegalArgumentException("Plano base inexistente: " + novo.codigoPlano()));
		Condominio novoCondominio = new Condominio(novo.nome(), novo.tipo());
		String cnpj = DocumentoBrasileiro.normalizar(novo.cnpj());
		if (cnpj != null) {
			if (!DocumentoBrasileiro.ehCnpj(cnpj)) {
				throw new IllegalArgumentException("O documento do condomínio deve ser um CNPJ");
			}
			novoCondominio.definirCnpj(cnpj);
		}
		Condominio condominio = condominios.save(novoCondominio);
		TenantAtual.executarComo(condominio.getId(), () -> assinaturas.save(
				new Assinatura(condominio, plano, novo.periodicidade(), StatusAssinatura.TESTE, LocalDate.now(relogio))));
		return condominio.getId();
	}

}
