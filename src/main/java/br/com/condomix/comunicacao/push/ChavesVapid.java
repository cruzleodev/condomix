package br.com.condomix.comunicacao.push;

import br.com.condomix.compartilhado.Cofre;
import br.com.condomix.comunicacao.dominio.ChavesDoPush;
import br.com.condomix.comunicacao.persistencia.ChavesDoPushRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;

/**
 * As chaves VAPID do Condomix: geradas no primeiro uso e guardadas no banco (a privada cifrada). Trocar as chaves
 * invalidaria todas as inscrições, então elas não mudam.
 */
@Component
public class ChavesVapid {

	private final ChavesDoPushRepository repositorio;

	private final Cofre cofre;

	private final TransactionTemplate transacao;

	private volatile Par par;

	public ChavesVapid(ChavesDoPushRepository repositorio, Cofre cofre, TransactionTemplate transacao) {
		this.repositorio = repositorio;
		this.cofre = cofre;
		this.transacao = transacao;
	}

	public record Par(String publica, PrivateKey privada) {
	}

	public Par par() {
		Par atual = par;
		if (atual == null) {
			atual = carregarOuGerar();
			par = atual;
		}
		return atual;
	}

	private Par carregarOuGerar() {
		ChavesDoPush guardadas = transacao.execute(s -> repositorio.findById(1).orElse(null));
		if (guardadas == null) {
			KeyPair novo = CriptografiaWebPush.novoParDeChaves();
			try {
				transacao.executeWithoutResult(s -> repositorio.save(new ChavesDoPush(
						CriptografiaWebPush.chavePublicaEmBase64((ECPublicKey) novo.getPublic()),
						cofre.cifrar(Base64.getEncoder().encodeToString(novo.getPrivate().getEncoded())))));
			}
			catch (DataIntegrityViolationException e) {
				// Outro servidor gerou ao mesmo tempo: vale a que ficou no banco.
			}
			guardadas = transacao.execute(s -> repositorio.findById(1).orElseThrow());
		}
		return new Par(guardadas.getChavePublica(),
				CriptografiaWebPush.chavePrivada(Base64.getDecoder().decode(cofre.decifrar(guardadas.getChavePrivada()))));
	}

}
