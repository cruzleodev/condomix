package br.com.condomix.acesso;

import br.com.condomix.acesso.persistencia.AcessoDeSuporteRepository;
import br.com.condomix.acesso.persistencia.UsuarioRepository;
import br.com.condomix.acesso.suporte.AcessoDeSuporte;
import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.plataforma.ConsultaDeCondominios;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
class AberturaDeSuporte implements SuporteAoCondominio {

	private final AcessoDeSuporteRepository acessos;

	private final UsuarioRepository usuarios;

	private final ConsultaDeCondominios condominios;

	private final EmissorDeToken emissor;

	private final Clock relogio;

	private final Duration validade;

	AberturaDeSuporte(AcessoDeSuporteRepository acessos, UsuarioRepository usuarios, ConsultaDeCondominios condominios,
			EmissorDeToken emissor, Clock relogio, @Value("${condomix.suporte.validade:1h}") Duration validade) {
		this.acessos = acessos;
		this.usuarios = usuarios;
		this.condominios = condominios;
		this.emissor = emissor;
		this.relogio = relogio;
		this.validade = validade;
	}

	@Override
	@Transactional
	public TokenDeSuporte abrir(UUID administradorId, UUID condominioId, String motivo) {
		Usuario administrador = usuarios.findById(administradorId)
				.filter(u -> u.isAtivo() && u.isAdminPlataforma())
				.orElseThrow(() -> RegraDeNegocioException.invalido("Só o administrador da plataforma abre acesso de suporte"));
		if (!condominios.existe(condominioId)) {
			throw new NaoEncontradoException("Condomínio não encontrado");
		}
		Instant agora = Instant.now(relogio);
		AcessoDeSuporte acesso;
		try {
			acesso = acessos.save(new AcessoDeSuporte(administradorId, condominioId, motivo, agora, agora.plus(validade)));
		}
		catch (IllegalArgumentException e) {
			throw RegraDeNegocioException.invalido(e.getMessage());
		}
		var token = emissor.emitir(administrador, condominioId, EnumSet.of(Perfil.SUPORTE), validade,
				Map.of(ConfiguracaoSeguranca.CLAIM_SUPORTE, acesso.getId().toString()));
		return new TokenDeSuporte(acesso.getId(), token.valor(), token.expiraEm());
	}

	@Override
	@Transactional(readOnly = true)
	public List<RegistroDeSuporte> historico() {
		List<AcessoDeSuporte> lista = acessos.findTop200ByOrderByInicioDesc();
		Map<UUID, String> emails = usuarios.findAllById(lista.stream().map(AcessoDeSuporte::getAdministradorId).toList())
				.stream().collect(Collectors.toMap(Usuario::getId, Usuario::getEmail, (a, b) -> a));
		return lista.stream()
				.map(a -> new RegistroDeSuporte(a.getId(), a.getAdministradorId(), emails.get(a.getAdministradorId()),
						a.getCondominioId(), a.getMotivo(), a.getInicio(), a.getExpiraEm()))
				.toList();
	}

}
