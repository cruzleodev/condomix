package br.com.condomix.financeiro.web;

import br.com.condomix.acesso.Permissoes;
import br.com.condomix.pagamentos.AmbienteDoGateway;
import br.com.condomix.pagamentos.TipoDeGateway;
import br.com.condomix.financeiro.aplicacao.ConsultaDeBoletos;
import br.com.condomix.financeiro.aplicacao.ContaDoGateway;
import br.com.condomix.financeiro.aplicacao.SolicitacaoDeBoletos;
import br.com.condomix.financeiro.dominio.Boleto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "Financeiro: boleto e Pix")
@RestController
@RequestMapping("/api")
class BoletoController {

	private final ConsultaDeBoletos consulta;

	private final SolicitacaoDeBoletos solicitacao;

	private final ContaDoGateway conta;

	BoletoController(ConsultaDeBoletos consulta, SolicitacaoDeBoletos solicitacao, ContaDoGateway conta) {
		this.consulta = consulta;
		this.solicitacao = solicitacao;
		this.conta = conta;
	}

	/**
	 * A 2ª via: tudo o que o morador precisa para pagar.
	 *
	 * @param valor      o valor do boleto; pago depois do vencimento, o banco acrescenta multa e juros
	 * @param pixQrCode  imagem PNG em Base64; vazio quando a conta do condomínio não tem chave Pix
	 * @param url        o boleto em PDF (vazio no gateway simulado)
	 */
	record FormasDePagamento(UUID cobrancaId, BigDecimal valor, LocalDate vencimento, String url, String linhaDigitavel,
			String pixCopiaECola, String pixQrCode, TipoDeGateway gateway) {

		static FormasDePagamento de(Boleto b) {
			return new FormasDePagamento(b.getCobrancaId(), b.getValor(), b.getVencimento(), b.getUrl(), b.getLinhaDigitavel(),
					b.getPixCopiaECola(), b.getPixQrCode(), b.getGateway());
		}

	}

	record BoletosPedidos(int pedidos) {
	}

	/** {@code ligado}: as cobranças novas já saem com boleto e Pix. */
	record GatewayNoCondominio(boolean ligado, TipoDeGateway gateway, AmbienteDoGateway ambiente) {
	}

	@Operation(summary = "Boleto e Pix de uma cobrança em aberto (2ª via), para a gestão")
	@PreAuthorize(Permissoes.CONSULTA_DO_CADASTRO)
	@GetMapping("/cobrancas/{id}/pagamento")
	FormasDePagamento daCobranca(@PathVariable UUID id) {
		return FormasDePagamento.de(consulta.daCobranca(id));
	}

	@Operation(summary = "Minha 2ª via: boleto e Pix de uma cobrança das minhas unidades")
	@GetMapping("/eu/cobrancas/{id}/pagamento")
	FormasDePagamento minha(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
		return FormasDePagamento.de(consulta.doPagador(UUID.fromString(jwt.getSubject()), id));
	}

	@Operation(summary = "Emitir os boletos que faltam",
			description = "Para as cobranças em aberto sem boleto (ex.: de antes de ligar o gateway) e as que falharam. "
					+ "As importadas da administradora ficam de fora. Os boletos ficam prontos em alguns segundos.")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping("/cobrancas/boletos")
	@ResponseStatus(HttpStatus.ACCEPTED)
	BoletosPedidos emitirOsQueFaltam() {
		return new BoletosPedidos(solicitacao.emitirOsQueFaltam());
	}

	@Operation(summary = "Tentar de novo um boleto que falhou (depois de corrigir o motivo)")
	@PreAuthorize(Permissoes.GESTAO)
	@PostMapping("/cobrancas/{id}/boleto/nova-tentativa")
	@ResponseStatus(HttpStatus.ACCEPTED)
	void tentarDeNovo(@PathVariable UUID id) {
		solicitacao.tentarDeNovo(id);
	}

	@Operation(summary = "Se o boleto e o Pix estão ligados para o condomínio")
	@PreAuthorize(Permissoes.CONSULTA_DO_CADASTRO)
	@GetMapping("/financeiro/gateway")
	GatewayNoCondominio gateway() {
		return conta.situacao()
				.map(c -> new GatewayNoCondominio(c.isAtiva(), c.getGateway(), c.getAmbiente()))
				.orElse(new GatewayNoCondominio(false, null, null));
	}

}
