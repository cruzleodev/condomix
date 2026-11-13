package br.com.condomix.acesso;

import br.com.condomix.acesso.persistencia.PerfilAcessoRepository;
import br.com.condomix.acesso.persistencia.PessoaRepository;
import br.com.condomix.acesso.persistencia.UnidadeRepository;
import br.com.condomix.acesso.persistencia.UsuarioRepository;
import br.com.condomix.acesso.persistencia.VinculoUnidadeRepository;
import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.plataforma.CadastroDeCondominios;
import br.com.condomix.plataforma.Periodicidade;
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

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Só no perfil dev: na primeira execução, cria dados para testar o login.
 *
 * <ul>
 *   <li><b>sindico@condomix.dev</b>: síndico de dois condomínios (precisa escolher um após o login)</li>
 *   <li><b>morador@condomix.dev</b>: morador do apto A-101 do Residencial Exemplo (entra direto)</li>
 * </ul>
 * A senha dos dois está em {@code condomix.dev.senha} (application-dev.yaml).
 *
 * <p>Cria só dados do módulo acesso. Os condomínios e as assinaturas são pedidos à plataforma pelo
 * caso de uso público {@link CadastroDeCondominios}.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "condomix.dev.dados-exemplo", havingValue = "true")
class DadosDeDesenvolvimento implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DadosDeDesenvolvimento.class);

	static final String EMAIL_SINDICO = "sindico@condomix.dev";

	static final String EMAIL_MORADOR = "morador@condomix.dev";

	private final CadastroDeCondominios cadastroDeCondominios;
	private final UnidadeRepository unidades;
	private final PessoaRepository pessoas;
	private final VinculoUnidadeRepository vinculos;
	private final UsuarioRepository usuarios;
	private final PerfilAcessoRepository perfis;
	private final PasswordEncoder senhas;
	private final Clock relogio;
	private final String senha;

	DadosDeDesenvolvimento(CadastroDeCondominios cadastroDeCondominios, UnidadeRepository unidades,
			PessoaRepository pessoas, VinculoUnidadeRepository vinculos, UsuarioRepository usuarios,
			PerfilAcessoRepository perfis, PasswordEncoder senhas, Clock relogio,
			@Value("${condomix.dev.senha}") String senha) {
		this.cadastroDeCondominios = cadastroDeCondominios;
		this.unidades = unidades;
		this.pessoas = pessoas;
		this.vinculos = vinculos;
		this.usuarios = usuarios;
		this.perfis = perfis;
		this.senhas = senhas;
		this.relogio = relogio;
		this.senha = senha;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (usuarios.buscarPorEmail(EMAIL_SINDICO).isPresent()) {
			return;
		}
		Usuario sindico = usuarios.save(new Usuario(EMAIL_SINDICO, senhas.encode(senha)));
		Usuario morador = usuarios.save(new Usuario(EMAIL_MORADOR, senhas.encode(senha)));

		UUID residencial = cadastroDeCondominios.cadastrar("Residencial Exemplo",
				TipoCondominio.RESIDENCIAL_VERTICAL, "COMPLETO", Periodicidade.MENSAL);
		UUID comercial = cadastroDeCondominios.cadastrar("Centro Comercial Exemplo",
				TipoCondominio.COMERCIAL, "ESSENCIAL", Periodicidade.ANUAL);

		TenantAtual.executarComo(residencial, () -> {
			Unidade apto101 = unidades.save(new Unidade(residencial, "A", "101", TipoUnidade.APARTAMENTO));
			Pessoa ana = pessoas.save(new Pessoa(residencial, "Ana Moradora"));
			vinculos.save(new VinculoUnidade(ana, apto101, TipoVinculo.PROPRIETARIO, LocalDate.now(relogio)));
			perfis.save(new PerfilAcesso(residencial, morador, Perfil.MORADOR, ana));
			return perfis.save(new PerfilAcesso(residencial, sindico, Perfil.SINDICO, null));
		});
		TenantAtual.executarComo(comercial, () -> perfis.save(new PerfilAcesso(comercial, sindico, Perfil.SINDICO, null)));

		log.info("Dados de exemplo criados. Logins: {} (2 condomínios) e {} (1 condomínio); senha em condomix.dev.senha",
				EMAIL_SINDICO, EMAIL_MORADOR);
	}

}
