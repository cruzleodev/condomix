package br.com.condomix;

import br.com.condomix.acesso.Perfil;
import br.com.condomix.acesso.PerfilAcesso;
import br.com.condomix.acesso.persistencia.PerfilAcessoRepository;
import br.com.condomix.acesso.Pessoa;
import br.com.condomix.acesso.persistencia.PessoaRepository;
import br.com.condomix.acesso.TipoUnidade;
import br.com.condomix.acesso.TipoVinculo;
import br.com.condomix.acesso.Unidade;
import br.com.condomix.acesso.persistencia.UnidadeRepository;
import br.com.condomix.acesso.Usuario;
import br.com.condomix.acesso.persistencia.UsuarioRepository;
import br.com.condomix.acesso.VinculoUnidade;
import br.com.condomix.acesso.persistencia.VinculoUnidadeRepository;
import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.plataforma.Assinatura;
import br.com.condomix.plataforma.CadastroDeCondominios;
import br.com.condomix.plataforma.ConsultaDeCondominios;
import br.com.condomix.plataforma.ResumoCondominio;
import br.com.condomix.plataforma.persistencia.AssinaturaRepository;
import br.com.condomix.plataforma.Condominio;
import br.com.condomix.plataforma.persistencia.CondominioRepository;
import br.com.condomix.plataforma.Endereco;
import br.com.condomix.plataforma.Modulo;
import br.com.condomix.plataforma.Periodicidade;
import br.com.condomix.plataforma.Plano;
import br.com.condomix.plataforma.persistencia.PlanoRepository;
import br.com.condomix.plataforma.StatusAssinatura;
import br.com.condomix.plataforma.TipoCondominio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Confere que a migração V1 e as entidades batem, e que o Hibernate separa os dados por condomínio.
 *
 * <p>Sem {@code @Transactional} de propósito: o tenant precisa estar definido antes de cada transação
 * começar (ver {@link TenantAtual}). Cada teste cria seus próprios condomínios, então não há conflito.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class CadastroBaseTests {

	@Autowired PlanoRepository planos;
	@Autowired CondominioRepository condominios;
	@Autowired AssinaturaRepository assinaturas;
	@Autowired UnidadeRepository unidades;
	@Autowired PessoaRepository pessoas;
	@Autowired VinculoUnidadeRepository vinculos;
	@Autowired UsuarioRepository usuarios;
	@Autowired PerfilAcessoRepository perfis;
	@Autowired CadastroDeCondominios cadastroDeCondominios;
	@Autowired ConsultaDeCondominios consultaDeCondominios;

	@Test
	void planosIniciaisLiberamOsModulosCertos() {
		Plano essencial = planos.findByCodigo("ESSENCIAL").orElseThrow();
		Plano completo = planos.findByCodigo("COMPLETO").orElseThrow();
		Plano whatsapp = planos.findByCodigo("WHATSAPP").orElseThrow();

		assertThat(essencial.getModulos()).containsExactlyInAnyOrder(Modulo.DOCUMENTOS, Modulo.FINANCEIRO);
		assertThat(completo.getModulos()).containsExactlyInAnyOrder(Modulo.DOCUMENTOS, Modulo.FINANCEIRO, Modulo.RESERVAS);
		assertThat(whatsapp.libera(Modulo.WHATSAPP)).isTrue();
		assertThat(essencial.libera(Modulo.RESERVAS)).isFalse();
	}

	@Test
	void gravaCadastroCompletoDeUmCondominio() {
		Condominio condominio = new Condominio("Residencial Jardim das Flores", TipoCondominio.RESIDENCIAL_VERTICAL);
		condominio.alterarEndereco(new Endereco("66000000", "Av. Exemplo", "100", null, "Centro", "Belém", "PA"));
		condominios.save(condominio);
		UUID tenant = condominio.getId();
		String email = "maria-" + UUID.randomUUID() + "@exemplo.com";
		Usuario login = usuarios.save(new Usuario("  " + email.toUpperCase() + " ", "{bcrypt}hash-de-teste"));

		var apto101 = TenantAtual.executarComo(tenant, () -> {
			Plano completo = planos.findByCodigo("COMPLETO").orElseThrow();
			assinaturas.save(new Assinatura(condominio, completo, Periodicidade.MENSAL, StatusAssinatura.ATIVA, LocalDate.now()));
			Unidade unidade = unidades.save(new Unidade(tenant, "A", "101", TipoUnidade.APARTAMENTO));
			Pessoa maria = pessoas.save(new Pessoa(tenant, "Maria Souza"));
			vinculos.save(new VinculoUnidade(maria, unidade, TipoVinculo.PROPRIETARIO, LocalDate.now()));
			perfis.save(new PerfilAcesso(tenant, login, Perfil.MORADOR, maria));
			return unidade;
		});

		TenantAtual.executarComo(tenant, () -> {
			assertThat(assinaturas.findByTenantId(tenant)).singleElement()
					.satisfies(a -> assertThat(a.getStatus()).isEqualTo(StatusAssinatura.ATIVA));
			assertThat(unidades.findByTenantIdOrderByBlocoAscIdentificacaoAsc(tenant)).hasSize(1);
			assertThat(vinculos.findByUnidadeId(apto101.getId())).hasSize(1);
			assertThat(perfis.findByUsuarioIdAndAtivoTrue(login.getId())).singleElement()
					.satisfies(p -> assertThat(p.getPerfil()).isEqualTo(Perfil.MORADOR));
			return null;
		});
		assertThat(usuarios.buscarPorEmail(email)).isPresent();
		assertThat(condominios.findById(tenant).orElseThrow().getEndereco().cidade()).isEqualTo("Belém");
	}

	@Test
	void umCondominioNaoEnxergaOsDadosDeOutro() {
		UUID a = condominios.save(new Condominio("Condomínio A", TipoCondominio.RESIDENCIAL_VERTICAL)).getId();
		UUID b = condominios.save(new Condominio("Condomínio B", TipoCondominio.RESIDENCIAL_VERTICAL)).getId();
		TenantAtual.executarComo(a, () -> unidades.save(new Unidade(a, null, "Apto A1", TipoUnidade.APARTAMENTO)));
		TenantAtual.executarComo(b, () -> unidades.save(new Unidade(b, null, "Apto B1", TipoUnidade.APARTAMENTO)));

		// Mesmo pedindo explicitamente os dados de A, quem está em B não recebe nada.
		assertThat(TenantAtual.executarComo(b, () -> unidades.findByTenantIdOrderByBlocoAscIdentificacaoAsc(a))).isEmpty();
		assertThat(TenantAtual.executarComo(b, () -> unidades.findAll()))
				.extracting(Unidade::getIdentificacao).containsExactly("Apto B1");
		// Sem condomínio nenhum, nada aparece.
		assertThat(unidades.findAll()).isEmpty();
		// O modo sistema enxerga os dois.
		assertThat(TenantAtual.comoSistema(() -> unidades.findAll()))
				.extracting(Unidade::getIdentificacao).contains("Apto A1", "Apto B1");
	}

	@Test
	void naoGravaEmOutroCondominioNemSemCondominio() {
		UUID a = condominios.save(new Condominio("Condomínio A", TipoCondominio.COMERCIAL)).getId();
		UUID b = condominios.save(new Condominio("Condomínio B", TipoCondominio.COMERCIAL)).getId();

		assertThatThrownBy(() -> TenantAtual.executarComo(b, () -> unidades.save(new Unidade(a, null, "Sala 1", TipoUnidade.SALA))))
				.as("estando em B, gravar uma unidade de A").isNotNull();
		assertThatThrownBy(() -> unidades.save(new Unidade(a, null, "Sala 2", TipoUnidade.SALA)))
				.as("gravar sem condomínio selecionado").isNotNull();
		assertThat(TenantAtual.comoSistema(() -> unidades.findAll()))
				.extracting(Unidade::getIdentificacao).doesNotContain("Sala 1", "Sala 2");
	}

	@Test
	void cadastroDeCondominioCriaAssinaturaEmTeste() {
		UUID id = cadastroDeCondominios.cadastrar("Novo Cliente", TipoCondominio.MISTO, "ESSENCIAL", Periodicidade.SEMESTRAL);

		assertThat(TenantAtual.executarComo(id, () -> assinaturas.findByTenantId(id))).singleElement().satisfies(a -> {
			assertThat(a.getStatus()).isEqualTo(StatusAssinatura.TESTE);
			assertThat(a.getPeriodicidade()).isEqualTo(Periodicidade.SEMESTRAL);
		});
		assertThatThrownBy(() -> cadastroDeCondominios.cadastrar("X", TipoCondominio.MISTO, "WHATSAPP", Periodicidade.MENSAL))
				.as("WhatsApp é adicional, não pode ser o plano base").isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void consultaDeCondominiosIgnoraInativos() {
		Condominio ativo = condominios.save(new Condominio("Ativo", TipoCondominio.COMERCIAL));
		Condominio inativo = new Condominio("Inativo", TipoCondominio.COMERCIAL);
		inativo.desativar();
		condominios.save(inativo);

		assertThat(consultaDeCondominios.ativosEntre(java.util.List.of(ativo.getId(), inativo.getId(), UUID.randomUUID())))
				.extracting(ResumoCondominio::nome).containsExactly("Ativo");
	}

	@Test
	void naoPermiteUnidadeRepetidaMesmoSemBloco() {
		UUID tenant = condominios.save(new Condominio("Vila das Palmeiras", TipoCondominio.RESIDENCIAL_HORIZONTAL)).getId();
		TenantAtual.executarComo(tenant, () -> unidades.saveAndFlush(new Unidade(tenant, null, "Casa 12", TipoUnidade.CASA)));

		assertThatThrownBy(() -> TenantAtual.executarComo(tenant,
				() -> unidades.saveAndFlush(new Unidade(tenant, null, "Casa 12", TipoUnidade.CASA))))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void naoVinculaPessoaAUnidadeDeOutroCondominio() {
		Pessoa pessoaDeA = new Pessoa(UUID.randomUUID(), "João");
		Unidade salaDeB = new Unidade(UUID.randomUUID(), null, "Sala 1", TipoUnidade.SALA);

		assertThatThrownBy(() -> new VinculoUnidade(pessoaDeA, salaDeB, TipoVinculo.INQUILINO, LocalDate.now()))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
