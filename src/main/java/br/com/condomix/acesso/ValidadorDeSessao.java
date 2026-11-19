package br.com.condomix.acesso;

import br.com.condomix.acesso.persistencia.AcessoDeSuporteRepository;
import br.com.condomix.acesso.persistencia.UsuarioRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Confere, a cada requisição, se o token ainda vale além da assinatura e do prazo:
 * <ul>
 *   <li>o login não foi bloqueado depois que o token foi emitido;</li>
 *   <li>no token de suporte, o acesso não foi encerrado antes do prazo.</li>
 * </ul>
 * Se não vale, a API responde 401 e o app volta ao login (ou ao painel, no caso do suporte).
 * São duas consultas pela chave primária em tabelas sem tenant, por isso rodam antes do filtro de tenant.
 */
@Component
class ValidadorDeSessao implements OAuth2TokenValidator<Jwt> {

	private static final OAuth2Error ENCERRADA = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
			"A sessão foi encerrada", null);

	private final UsuarioRepository usuarios;

	private final AcessoDeSuporteRepository acessosDeSuporte;

	ValidadorDeSessao(UsuarioRepository usuarios, AcessoDeSuporteRepository acessosDeSuporte) {
		this.usuarios = usuarios;
		this.acessosDeSuporte = acessosDeSuporte;
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt token) {
		if (!usuarios.existsByIdAndAtivoTrue(UUID.fromString(token.getSubject()))) {
			return OAuth2TokenValidatorResult.failure(ENCERRADA);
		}
		String suporte = token.getClaimAsString(ConfiguracaoSeguranca.CLAIM_SUPORTE);
		if (suporte != null && !acessosDeSuporte.existsByIdAndEncerradoEmIsNull(UUID.fromString(suporte))) {
			return OAuth2TokenValidatorResult.failure(ENCERRADA);
		}
		return OAuth2TokenValidatorResult.success();
	}

}
