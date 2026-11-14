package br.com.condomix.acesso;

import br.com.condomix.acesso.mandato.Mandato;
import br.com.condomix.acesso.persistencia.MandatoRepository;
import br.com.condomix.acesso.persistencia.PerfilAcessoRepository;
import br.com.condomix.compartilhado.TenantAtual;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Calcula, para cada condomínio, os papéis de um usuário hoje. Junta duas fontes:
 * <ul>
 *   <li>os perfis gravados ({@link PerfilAcesso}: morador, administradora, portaria);</li>
 *   <li>os cargos dos mandatos com acesso na data (síndico, subsíndico, conselheiro), inclusive prorrogados.</li>
 * </ul>
 * Assim, o morador que é síndico recebe MORADOR e SINDICO, e o síndico profissional só SINDICO.
 * Roda em modo sistema porque precisa enxergar todos os condomínios do usuário.
 */
@Component
class PapeisDoUsuario {

	private final PerfilAcessoRepository perfis;

	private final MandatoRepository mandatos;

	private final Clock relogio;

	PapeisDoUsuario(PerfilAcessoRepository perfis, MandatoRepository mandatos, Clock relogio) {
		this.perfis = perfis;
		this.mandatos = mandatos;
		this.relogio = relogio;
	}

	Map<UUID, Set<Perfil>> porCondominio(UUID usuarioId) {
		LocalDate hoje = LocalDate.now(relogio);
		Map<UUID, Set<Perfil>> resultado = new HashMap<>();
		TenantAtual.comoSistema(() -> perfis.findByUsuarioIdAndAtivoTrue(usuarioId))
				.forEach(p -> adicionar(resultado, p.getTenantId(), p.getPerfil()));
		TenantAtual.comoSistema(() -> mandatos.comAcessoNaData(usuarioId, hoje))
				.forEach((Mandato m) -> adicionar(resultado, m.getTenantId(), m.getCargo().perfil()));
		return resultado;
	}

	private static void adicionar(Map<UUID, Set<Perfil>> mapa, UUID condominio, Perfil perfil) {
		mapa.computeIfAbsent(condominio, c -> EnumSet.noneOf(Perfil.class)).add(perfil);
	}

}
