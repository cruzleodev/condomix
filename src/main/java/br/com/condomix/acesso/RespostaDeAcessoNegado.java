package br.com.condomix.acesso;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Resposta 403 com o motivo, no formato RFC 9457, em vez de uma resposta vazia.
 * <ul>
 *   <li>Token sem condomínio: diz para escolher um em {@code POST /api/auth/condominio}.</li>
 *   <li>Token de suporte tentando gravar: diz que o acesso é só de leitura.</li>
 *   <li>Demais casos: o perfil não permite a ação.</li>
 * </ul>
 */
class RespostaDeAcessoNegado implements AccessDeniedHandler {

	static final String SEM_CONDOMINIO =
			"Escolha um condomínio em POST /api/auth/condominio antes de usar esta rota";

	static final String SUPORTE_SO_LEITURA = "O acesso de suporte é somente leitura";

	static final String SEM_PERMISSAO = "Seu perfil não permite esta ação";

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException negado)
			throws IOException {
		response.setStatus(HttpServletResponse.SC_FORBIDDEN);
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("""
				{"type":"about:blank","title":"Forbidden","status":403,"detail":"%s","instance":"%s"}"""
				.formatted(motivo(request), json(request.getRequestURI())));
	}

	private static String motivo(HttpServletRequest request) {
		if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token)) {
			return SEM_PERMISSAO;
		}
		boolean rotaDeCondominio = !request.getRequestURI().startsWith("/api/plataforma/");
		if (rotaDeCondominio && !token.getToken().hasClaim(ConfiguracaoSeguranca.CLAIM_CONDOMINIO)) {
			return SEM_CONDOMINIO;
		}
		if (token.getToken().hasClaim(ConfiguracaoSeguranca.CLAIM_SUPORTE) && !HttpMethod.GET.matches(request.getMethod())) {
			return SUPORTE_SO_LEITURA;
		}
		return SEM_PERMISSAO;
	}

	/** O endereço vai dentro de um JSON: escapa aspas e barras invertidas. */
	private static String json(String texto) {
		return texto.replace("\\", "\\\\").replace("\"", "\\\"");
	}

}
