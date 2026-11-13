package br.com.condomix;

import br.com.condomix.acesso.Perfil;
import br.com.condomix.acesso.PerfilAcesso;
import br.com.condomix.acesso.PerfilAcessoRepository;
import br.com.condomix.acesso.Pessoa;
import br.com.condomix.acesso.PessoaRepository;
import br.com.condomix.acesso.TipoUnidade;
import br.com.condomix.acesso.TipoVinculo;
import br.com.condomix.acesso.Unidade;
import br.com.condomix.acesso.UnidadeRepository;
import br.com.condomix.acesso.Usuario;
import br.com.condomix.acesso.UsuarioRepository;
import br.com.condomix.acesso.VinculoUnidade;
import br.com.condomix.acesso.VinculoUnidadeRepository;
import br.com.condomix.plataforma.Assinatura;
import br.com.condomix.plataforma.AssinaturaRepository;
import br.com.condomix.plataforma.Condominio;
import br.com.condomix.plataforma.CondominioRepository;
import br.com.condomix.plataforma.Endereco;
import br.com.condomix.plataforma.Modulo;
import br.com.condomix.plataforma.Periodicidade;
import br.com.condomix.plataforma.Plano;
import br.com.condomix.plataforma.PlanoRepository;
import br.com.condomix.plataforma.StatusAssinatura;
import br.com.condomix.plataforma.TipoCondominio;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Confere que a migração V1 e as entidades batem, gravando e lendo um cadastro completo. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class CadastroBaseTests {

	@Autowired PlanoRepository planos;
	@Autowired CondominioRepository condominios;
	@Autowired AssinaturaRepository assinaturas;
	@Autowired UnidadeRepository unidades;
	@Autowired PessoaRepository pessoas;
	@Autowired VinculoUnidadeRepository vinculos;
	@Autowired UsuarioRepository usuarios;
	@Autowired PerfilAcessoRepository perfis;
	@Autowired EntityManager em;

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

		Plano completo = planos.findByCodigo("COMPLETO").orElseThrow();
		assinaturas.save(new Assinatura(condominio, completo, Periodicidade.MENSAL, StatusAssinatura.ATIVA, LocalDate.now()));

		var tenant = condominio.getId();
		Unidade apto101 = unidades.save(new Unidade(tenant, "A", "101", TipoUnidade.APARTAMENTO));
		Pessoa maria = pessoas.save(new Pessoa(tenant, "Maria Souza"));
		vinculos.save(new VinculoUnidade(maria, apto101, TipoVinculo.PROPRIETARIO, LocalDate.now()));

		Usuario login = usuarios.save(new Usuario("  Maria@Exemplo.com ", "{bcrypt}hash-de-teste"));
		perfis.save(new PerfilAcesso(tenant, login, Perfil.MORADOR, maria));

		em.flush();
		em.clear();

		assertThat(assinaturas.findByTenantId(tenant)).singleElement()
				.satisfies(a -> assertThat(a.getPlano().getCodigo()).isEqualTo("COMPLETO"));
		assertThat(unidades.findByTenantIdOrderByBlocoAscIdentificacaoAsc(tenant)).hasSize(1);
		assertThat(vinculos.findByUnidadeId(apto101.getId())).singleElement()
				.satisfies(v -> assertThat(v.getPessoa().getNome()).isEqualTo("Maria Souza"));
		assertThat(usuarios.buscarPorEmail("MARIA@exemplo.com")).isPresent();
		assertThat(perfis.findByUsuarioIdAndAtivoTrue(login.getId())).singleElement()
				.satisfies(p -> assertThat(p.getTenantId()).isEqualTo(tenant));
		assertThat(condominios.findById(tenant).orElseThrow().getEndereco().cidade()).isEqualTo("Belém");
	}

	@Test
	void naoPermiteUnidadeRepetidaMesmoSemBloco() {
		Condominio condominio = condominios.save(new Condominio("Vila das Palmeiras", TipoCondominio.RESIDENCIAL_HORIZONTAL));
		unidades.saveAndFlush(new Unidade(condominio.getId(), null, "Casa 12", TipoUnidade.CASA));

		assertThatThrownBy(() -> unidades.saveAndFlush(new Unidade(condominio.getId(), null, "Casa 12", TipoUnidade.CASA)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void naoVinculaPessoaAUnidadeDeOutroCondominio() {
		Condominio a = condominios.save(new Condominio("Condomínio A", TipoCondominio.COMERCIAL));
		Condominio b = condominios.save(new Condominio("Condomínio B", TipoCondominio.COMERCIAL));
		Pessoa pessoaDeA = pessoas.save(new Pessoa(a.getId(), "João"));
		Unidade salaDeB = unidades.save(new Unidade(b.getId(), null, "Sala 1", TipoUnidade.SALA));

		assertThatThrownBy(() -> new VinculoUnidade(pessoaDeA, salaDeB, TipoVinculo.INQUILINO, LocalDate.now()))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
