package br.com.condomix.comunicacao.aplicacao;

import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.comunicacao.dominio.InscricaoPush;
import br.com.condomix.comunicacao.persistencia.InscricaoPushRepository;
import br.com.condomix.comunicacao.push.ChavesVapid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.UUID;

/** Liga e desliga o push num aparelho. */
@Service
public class InscricoesPush {

	private final InscricaoPushRepository inscricoes;

	private final ChavesVapid chaves;

	public InscricoesPush(InscricaoPushRepository inscricoes, ChavesVapid chaves) {
		this.inscricoes = inscricoes;
		this.chaves = chaves;
	}

	/** A chave pública VAPID que o navegador pede para se inscrever. */
	public String chavePublica() {
		return chaves.par().publica();
	}

	@Transactional
	public void inscrever(UUID usuarioId, String endpoint, String p256dh, String auth) {
		URI uri;
		try {
			uri = URI.create(endpoint);
		}
		catch (IllegalArgumentException e) {
			throw RegraDeNegocioException.invalido("Endereço de push inválido");
		}
		if (!"https".equals(uri.getScheme()) && !"localhost".equals(uri.getHost())) {
			throw RegraDeNegocioException.invalido("O endereço do serviço de push precisa ser https");
		}
		inscricoes.findByEndpoint(endpoint).ifPresentOrElse(i -> i.renovar(usuarioId, p256dh, auth),
				() -> inscricoes.save(new InscricaoPush(usuarioId, endpoint, p256dh, auth)));
	}

	@Transactional
	public void cancelar(UUID usuarioId, String endpoint) {
		inscricoes.findByEndpoint(endpoint).filter(i -> i.getUsuarioId().equals(usuarioId)).ifPresent(inscricoes::delete);
	}

	@Transactional(readOnly = true)
	public boolean temAparelho(UUID usuarioId) {
		return inscricoes.existsByUsuarioId(usuarioId);
	}

}
