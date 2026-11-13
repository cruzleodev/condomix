package br.com.condomix.acesso;

import br.com.condomix.compartilhado.TenantAtual;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Lê o condomínio do token JWT e roda o resto da requisição dentro dele (ver {@link TenantAtual}).
 * A partir daqui, o Hibernate só enxerga os dados desse condomínio.
 */
class FiltroDeTenant extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token
				&& token.getToken().hasClaim(ConfiguracaoSeguranca.CLAIM_CONDOMINIO)) {
			UUID tenantId = UUID.fromString(token.getToken().getClaimAsString(ConfiguracaoSeguranca.CLAIM_CONDOMINIO));
			try {
				TenantAtual.executarComo(tenantId, () -> {
					chain.doFilter(request, response);
					return null;
				});
			}
			catch (IOException | ServletException | RuntimeException e) {
				throw e;
			}
			catch (Exception e) {
				throw new ServletException(e);
			}
			return;
		}
		chain.doFilter(request, response);
	}

}
