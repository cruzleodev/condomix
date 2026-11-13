package br.com.condomix.acesso;

import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.plataforma.Assinatura;
import br.com.condomix.plataforma.AssinaturaRepository;
import br.com.condomix.plataforma.Condominio;
import br.com.condomix.plataforma.CondominioRepository;
import br.com.condomix.plataforma.Periodicidade;
import br.com.condomix.plataforma.PlanoRepository;
import br.com.condomix.plataforma.StatusAssinatura;
import br.com.condomix.plataforma.TipoCondominio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Só no perfil dev: na primeira execução, cria dados para testar o login.
 *
 * <ul>
 *   <li><b>sindico@condomix.dev</b>: síndico de dois condomínios (precisa escolher um após o login)</li>
 *   <li><b>morador@condomix.dev</b>: morador do apto 101 do Residencial Exemplo (entra direto)</li>
 * </ul>
 * A senha dos dois está em {@code condomix.dev.senha} (application-dev.yaml).
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "condomix.dev.dados-exemplo", havingValue = "true")
class DadosDeDesenvolvimento implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DadosDeDesenvolvimento.class);

	static final String EMAIL_SINDICO = "sindico@condomix.dev";

	static final String EMAIL_MORADOR = "morador@condomix.dev";

	private final CondominioRepository condominios;
	private final PlanoRepository planos;
	private final AssinaturaRepository assinaturas;
	private final UnidadeRepository unidades;
	private final PessoaRepository pessoas;
	private final VinculoUnidadeRepository vinculos;
	private final UsuarioRepository usuarios;
	private final PerfilAcessoRepository perfis;
	private final PasswordEncoder senhas;
	private final String senha;

	DadosDeDesenvolvimento(CondominioRepository condominios, PlanoRepository planos, AssinaturaRepository assinaturas,
			UnidadeRepository unidades, PessoaRepository pessoas, VinculoUnidadeRepository vinculos,
			UsuarioRepository usuarios, PerfilAcessoRepository perfis, PasswordEncoder senhas,
			@Value("${condomix.dev.senha}") String senha) {
		this.condominios = condominios;
		this.planos = planos;
		this.assinaturas = assinaturas;
		this.unidades = unidades;
		this.pessoas = pessoas;
		this.vinculos = vinculos;
		this.usuarios = usuarios;
		this.perfis = perfis;
		this.senhas = senhas;
		this.senha = senha;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (usuarios.buscarPorEmail(EMAIL_SINDICO).isPresent()) {
			return;
		}
		Usuario sindico = usuarios.save(new Usuario(EMAIL_SINDICO, senhas.encode(senha)));
		Usuario morador = usuarios.save(new Usuario(EMAIL_MORADOR, senhas.encode(senha)));

		Condominio residencial = condominios.save(new Condominio("Residencial Exemplo", TipoCondominio.RESIDENCIAL_VERTICAL));
		Condominio comercial = condominios.save(new Condominio("Centro Comercial Exemplo", TipoCondominio.COMERCIAL));

		TenantAtual.executarComo(residencial.getId(), () -> {
			assinaturas.save(new Assinatura(residencial, planos.findByCodigo("COMPLETO").orElseThrow(),
					Periodicidade.MENSAL, StatusAssinatura.TESTE, LocalDate.now()));
			Unidade apto101 = unidades.save(new Unidade(residencial.getId(), "A", "101", TipoUnidade.APARTAMENTO));
			Pessoa ana = pessoas.save(new Pessoa(residencial.getId(), "Ana Moradora"));
			vinculos.save(new VinculoUnidade(ana, apto101, TipoVinculo.PROPRIETARIO, LocalDate.now()));
			perfis.save(new PerfilAcesso(residencial.getId(), morador, Perfil.MORADOR, ana));
			perfis.save(new PerfilAcesso(residencial.getId(), sindico, Perfil.SINDICO, null));
			return null;
		});
		TenantAtual.executarComo(comercial.getId(), () -> {
			assinaturas.save(new Assinatura(comercial, planos.findByCodigo("ESSENCIAL").orElseThrow(),
					Periodicidade.ANUAL, StatusAssinatura.TESTE, LocalDate.now()));
			perfis.save(new PerfilAcesso(comercial.getId(), sindico, Perfil.SINDICO, null));
			return null;
		});
		log.info("Dados de exemplo criados. Logins: {} (2 condomínios) e {} (1 condomínio); senha em condomix.dev.senha",
				EMAIL_SINDICO, EMAIL_MORADOR);
	}

}
