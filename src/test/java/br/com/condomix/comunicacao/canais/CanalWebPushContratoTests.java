package br.com.condomix.comunicacao.canais;

import br.com.condomix.comunicacao.aplicacao.PropriedadesDasNotificacoes;
import br.com.condomix.comunicacao.dominio.InscricaoPush;
import br.com.condomix.comunicacao.persistencia.InscricaoPushRepository;
import br.com.condomix.comunicacao.push.AparelhoDeTeste;
import br.com.condomix.comunicacao.push.ChavesVapid;
import br.com.condomix.comunicacao.push.CriptografiaWebPush;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Com um serviço de push de mentira (servidor HTTP local) e um aparelho que decifra a mensagem como o navegador. */
class CanalWebPushContratoTests extends CanalDeNotificacaoContrato {

	private final AparelhoDeTeste aparelho = new AparelhoDeTeste();

	private final InscricaoPushRepository inscricoes = mock(InscricaoPushRepository.class);

	private final List<String> recebidas = new ArrayList<>();

	private final List<String> autorizacoes = new ArrayList<>();

	private HttpServer servicoDePush;

	private int status = 201;

	private CanalWebPush canal;

	/** Roda o código na hora, sem banco. */
	private static final TransactionTemplate SEM_BANCO = new TransactionTemplate() {
		@Override
		public <T> T execute(TransactionCallback<T> acao) {
			return acao.doInTransaction(mock(TransactionStatus.class));
		}
	};

	@BeforeEach
	void subirServicoDePush() throws Exception {
		servicoDePush = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		servicoDePush.createContext("/push", troca -> {
			byte[] corpo = troca.getRequestBody().readAllBytes();
			try {
				recebidas.add(aparelho.decifrar(corpo));
			}
			catch (Exception e) {
				recebidas.add("NÃO DECIFROU: " + e.getMessage());
			}
			autorizacoes.add(troca.getRequestHeaders().getFirst("Authorization") + " | " + troca.getRequestHeaders().getFirst("Content-Encoding"));
			troca.sendResponseHeaders(status, -1);
			troca.close();
		});
		servicoDePush.start();
		String endpoint = "http://localhost:" + servicoDePush.getAddress().getPort() + "/push/aparelho-da-ana";
		when(inscricoes.findByUsuarioId(any())).thenReturn(List.of(new InscricaoPush(ANA.usuarioId(), endpoint, aparelho.p256dh(), aparelho.auth())));
		KeyPair vapid = CriptografiaWebPush.novoParDeChaves();
		ChavesVapid chaves = mock(ChavesVapid.class);
		when(chaves.par()).thenReturn(new ChavesVapid.Par(CriptografiaWebPush.chavePublicaEmBase64((ECPublicKey) vapid.getPublic()),
				vapid.getPrivate()));
		canal = new CanalWebPush(inscricoes, chaves, new PropriedadesDasNotificacoes(null, null, null, false), SEM_BANCO,
				Clock.systemUTC());
	}

	@AfterEach
	void pararServicoDePush() {
		servicoDePush.stop(0);
	}

	@Override
	protected CanalDeNotificacao canal() {
		return canal;
	}

	@Override
	protected void provedorForaDoAr() {
		status = 503;
	}

	@Override
	protected String entregue() {
		assertThat(autorizacoes.getLast()).startsWith("vapid t=").endsWith("| aes128gcm");
		assertThat(recebidas.getLast()).contains("\"link\":\"/reservas\"");
		return recebidas.getLast();
	}

	@Test
	void semAparelhoInscritoNaoEFalha() {
		when(inscricoes.findByUsuarioId(any())).thenReturn(List.of());
		assertThat(canal.entregar(CONTEUDO, ANA)).isFalse();
	}

	@Test
	void aparelhoQueNaoExisteMaisTemAInscricaoApagada() {
		status = 410;
		assertThat(canal.entregar(CONTEUDO, ANA)).isFalse();
		verify(inscricoes).deleteById(any());
	}

}
