package br.com.condomix.comunicacao.canais;

import br.com.condomix.acesso.DestinatariosDeNotificacoes.Destinatario;
import br.com.condomix.comunicacao.aplicacao.PropriedadesDasNotificacoes;
import br.com.condomix.comunicacao.dominio.Canal;
import br.com.condomix.comunicacao.dominio.InscricaoPush;
import br.com.condomix.comunicacao.persistencia.InscricaoPushRepository;
import br.com.condomix.comunicacao.push.ChavesVapid;
import br.com.condomix.comunicacao.push.CriptografiaWebPush;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Web Push: o navegador (ou o app instalado) recebe a notificação mesmo fechado. Vai para todos os aparelhos em que
 * a pessoa ligou o push. Aparelho que não existe mais (resposta 404 ou 410) tem a inscrição apagada.
 */
@Component
class CanalWebPush implements CanalDeNotificacao {

	private static final Logger log = LoggerFactory.getLogger(CanalWebPush.class);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final InscricaoPushRepository inscricoes;

	private final ChavesVapid chaves;

	private final PropriedadesDasNotificacoes propriedades;

	private final TransactionTemplate transacao;

	private final Clock relogio;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	CanalWebPush(InscricaoPushRepository inscricoes, ChavesVapid chaves, PropriedadesDasNotificacoes propriedades,
			TransactionTemplate transacao, Clock relogio) {
		this.inscricoes = inscricoes;
		this.chaves = chaves;
		this.propriedades = propriedades;
		this.transacao = transacao;
		this.relogio = relogio;
	}

	@Override
	public Canal canal() {
		return Canal.PUSH;
	}

	@Override
	public boolean disponivel() {
		return true;
	}

	@Override
	public boolean entregar(ConteudoDaNotificacao conteudo, Destinatario destinatario) {
		List<InscricaoPush> aparelhos = transacao.execute(s -> inscricoes.findByUsuarioId(destinatario.usuarioId()));
		if (aparelhos == null || aparelhos.isEmpty()) {
			return false;
		}
		byte[] mensagem = JSON.writeValueAsString(Map.of("titulo", conteudo.titulo(), "texto", conteudo.texto(),
				"link", conteudo.link() == null ? "/" : conteudo.link(), "id", conteudo.notificacaoId().toString()))
				.getBytes(StandardCharsets.UTF_8);
		var par = chaves.par();
		int entregues = 0;
		RuntimeException ultimaFalha = null;
		for (InscricaoPush aparelho : aparelhos) {
			try {
				HttpRequest pedido = HttpRequest.newBuilder(java.net.URI.create(aparelho.getEndpoint()))
						.timeout(Duration.ofSeconds(15))
						.header("Content-Encoding", "aes128gcm")
						.header("Content-Type", "application/octet-stream")
						.header("TTL", "86400")
						.header("Urgency", "normal")
						.header("Authorization", CriptografiaWebPush.autorizacao(aparelho.getEndpoint(), par.privada(),
								par.publica(), propriedades.contatoPush(), Instant.now(relogio).plus(Duration.ofHours(12))))
						.POST(HttpRequest.BodyPublishers.ofByteArray(CriptografiaWebPush.cifrar(mensagem, aparelho.getP256dh(),
								aparelho.getAuth())))
						.build();
				int status = http.send(pedido, HttpResponse.BodyHandlers.discarding()).statusCode();
				if (status == 404 || status == 410) {
					transacao.executeWithoutResult(s -> inscricoes.deleteById(aparelho.getId()));
					log.info("Inscrição de push expirada apagada ({})", status);
				}
				else if (status >= 200 && status < 300) {
					entregues++;
				}
				else {
					ultimaFalha = new FalhaNaEntrega("O serviço de push recusou (" + status + ")");
				}
			}
			catch (IOException e) {
				ultimaFalha = new FalhaNaEntrega("O serviço de push não respondeu", e);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new FalhaNaEntrega("Envio interrompido", e);
			}
		}
		if (entregues == 0 && ultimaFalha != null) {
			throw ultimaFalha;
		}
		return entregues > 0;
	}

}
