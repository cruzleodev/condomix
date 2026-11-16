package br.com.condomix.acesso;

import br.com.condomix.acesso.persistencia.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Cria o primeiro administrador da plataforma ao subir a aplicação, se ainda não houver nenhum.
 * É o único passo feito fora do sistema: os demais administradores são criados por ele.
 *
 * <p>Usa {@code condomix.admin.email} e {@code condomix.admin.senha} (em produção: variáveis
 * {@code CONDOMIX_ADMIN_EMAIL} e {@code CONDOMIX_ADMIN_SENHA}). Se o e-mail já tiver login, só promove.
 */
@Component
class AdministradorInicial implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdministradorInicial.class);

	static final int TAMANHO_MINIMO_DA_SENHA = 10;

	private final UsuarioRepository usuarios;

	private final PasswordEncoder senhas;

	private final String email;

	private final String senha;

	AdministradorInicial(UsuarioRepository usuarios, PasswordEncoder senhas,
			@Value("${condomix.admin.email:}") String email, @Value("${condomix.admin.senha:}") String senha) {
		this.usuarios = usuarios;
		this.senhas = senhas;
		this.email = email;
		this.senha = senha;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (email.isBlank() || usuarios.existsByAdminPlataformaTrue()) {
			return;
		}
		Usuario usuario = usuarios.buscarPorEmail(email).orElseGet(() -> {
			if (senha.length() < TAMANHO_MINIMO_DA_SENHA) {
				throw new IllegalStateException("condomix.admin.senha precisa ter pelo menos "
						+ TAMANHO_MINIMO_DA_SENHA + " caracteres (defina CONDOMIX_ADMIN_SENHA)");
			}
			return new Usuario(email, senhas.encode(senha));
		});
		usuario.tornarAdministradorDaPlataforma();
		usuarios.save(usuario);
		log.info("Administrador da plataforma criado: {}", usuario.getEmail());
	}

}
