package br.com.condomix.comunicacao.web;

import br.com.condomix.compartilhado.EnvioDeEmail;
import br.com.condomix.comunicacao.aplicacao.ConsultaDeNotificacoes;
import br.com.condomix.comunicacao.aplicacao.InscricoesPush;
import br.com.condomix.comunicacao.aplicacao.PreferenciasDeNotificacoes;
import br.com.condomix.comunicacao.aplicacao.PreferenciasDeNotificacoes.Escolha;
import br.com.condomix.comunicacao.dominio.Canal;
import br.com.condomix.comunicacao.dominio.GrupoDeNotificacao;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/** O sino do app, as preferências de e-mail e push e a inscrição do aparelho no push. Sempre de quem está logado. */
@Tag(name = "Notificações")
@RestController
@RequestMapping("/api/notificacoes")
class NotificacaoController {

	private final ConsultaDeNotificacoes consulta;

	private final PreferenciasDeNotificacoes preferencias;

	private final InscricoesPush push;

	private final EnvioDeEmail email;

	NotificacaoController(ConsultaDeNotificacoes consulta, PreferenciasDeNotificacoes preferencias, InscricoesPush push,
			EnvioDeEmail email) {
		this.consulta = consulta;
		this.preferencias = preferencias;
		this.push = push;
		this.email = email;
	}

	record NotificacaoResposta(UUID id, GrupoDeNotificacao grupo, String titulo, String texto, String link, Instant criadoEm,
			boolean lida) {
	}

	record NotificacoesNaoLidas(long quantidade) {
	}

	record PreferenciaDoGrupo(GrupoDeNotificacao grupo, String rotulo, String exemplos, boolean email, boolean push) {
	}

	/**
	 * @param emailLigado    se o envio de e-mail está configurado nesta instalação
	 * @param aparelhoComPush se a pessoa já ligou o push em algum aparelho
	 */
	record PreferenciasDeNotificacao(List<PreferenciaDoGrupo> grupos, boolean emailLigado, boolean aparelhoComPush) {
	}

	record PedidoPreferencia(@NotNull GrupoDeNotificacao grupo, boolean email, boolean push) {
	}

	record ChaveDoPush(String chavePublica) {
	}

	record PedidoInscricaoNoPush(@NotBlank @Size(max = 1000) String endpoint, @NotBlank @Size(max = 200) String p256dh,
			@NotBlank @Size(max = 100) String auth) {
	}

	record PedidoCancelamentoDoPush(@NotBlank @Size(max = 1000) String endpoint) {
	}

	@Operation(summary = "Minhas notificações neste condomínio (as 50 mais recentes)")
	@GetMapping
	List<NotificacaoResposta> recentes(@AuthenticationPrincipal Jwt jwt) {
		return consulta.recentes(usuario(jwt)).stream()
				.map(n -> new NotificacaoResposta(n.getId(), n.getGrupo(), n.getTitulo(), n.getTexto(), n.getLink(), n.getCriadoEm(),
						n.getLidoEm() != null))
				.toList();
	}

	@Operation(summary = "Quantas não lidas (o número no sino)")
	@GetMapping("/nao-lidas")
	NotificacoesNaoLidas naoLidas(@AuthenticationPrincipal Jwt jwt) {
		return new NotificacoesNaoLidas(consulta.naoLidas(usuario(jwt)));
	}

	@Operation(summary = "Marcar uma notificação como lida")
	@PostMapping("/{id}/leitura")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void marcarLida(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
		consulta.marcarLida(id, usuario(jwt));
	}

	@Operation(summary = "Marcar todas como lidas")
	@PostMapping("/leitura")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void marcarTodas(@AuthenticationPrincipal Jwt jwt) {
		consulta.marcarTodasLidas(usuario(jwt));
	}

	@Operation(summary = "O que eu recebo por e-mail e push (no app, sempre)", description = "Vale em todos os meus condomínios.")
	@GetMapping("/preferencias")
	PreferenciasDeNotificacao preferencias(@AuthenticationPrincipal Jwt jwt) {
		return montar(usuario(jwt), preferencias.doUsuario(usuario(jwt)));
	}

	@Operation(summary = "Mudar o que eu recebo por e-mail e push")
	@PutMapping("/preferencias")
	PreferenciasDeNotificacao alterarPreferencias(@Valid @RequestBody List<@Valid PedidoPreferencia> pedido,
			@AuthenticationPrincipal Jwt jwt) {
		List<Escolha> escolhas = pedido.stream()
				.flatMap(p -> Stream.of(new Escolha(p.grupo(), Canal.EMAIL, p.email()), new Escolha(p.grupo(), Canal.PUSH, p.push())))
				.toList();
		return montar(usuario(jwt), preferencias.alterar(usuario(jwt), escolhas));
	}

	@Operation(summary = "A chave pública (VAPID) para o navegador ligar o push")
	@GetMapping("/push/chave")
	ChaveDoPush chaveDoPush() {
		return new ChaveDoPush(push.chavePublica());
	}

	@Operation(summary = "Ligar o push neste aparelho", description = "Os dados vêm do PushSubscription do navegador.")
	@PostMapping("/push/inscricao")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void inscrever(@Valid @RequestBody PedidoInscricaoNoPush p, @AuthenticationPrincipal Jwt jwt) {
		push.inscrever(usuario(jwt), p.endpoint(), p.p256dh(), p.auth());
	}

	@Operation(summary = "Desligar o push neste aparelho")
	@PostMapping("/push/cancelamento")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void cancelarPush(@Valid @RequestBody PedidoCancelamentoDoPush p, @AuthenticationPrincipal Jwt jwt) {
		push.cancelar(usuario(jwt), p.endpoint());
	}

	private PreferenciasDeNotificacao montar(UUID usuarioId, List<Escolha> escolhas) {
		List<PreferenciaDoGrupo> grupos = Arrays.stream(GrupoDeNotificacao.values())
				.map(g -> new PreferenciaDoGrupo(g, g.rotulo(), g.exemplos(), ativo(escolhas, g, Canal.EMAIL), ativo(escolhas, g, Canal.PUSH)))
				.toList();
		return new PreferenciasDeNotificacao(grupos, email.disponivel(), push.temAparelho(usuarioId));
	}

	private static boolean ativo(List<Escolha> escolhas, GrupoDeNotificacao grupo, Canal canal) {
		return escolhas.stream().filter(e -> e.grupo() == grupo && e.canal() == canal).findFirst().map(Escolha::ativo).orElse(true);
	}

	private static UUID usuario(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

}
