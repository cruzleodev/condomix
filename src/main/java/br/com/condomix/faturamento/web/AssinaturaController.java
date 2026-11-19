package br.com.condomix.faturamento.web;

import br.com.condomix.acesso.Permissoes;
import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.faturamento.FaturasDosClientes.PagamentoDaFatura;
import br.com.condomix.faturamento.SituacaoDaFatura;
import br.com.condomix.faturamento.aplicacao.ConsultaDeFaturas;
import br.com.condomix.plataforma.AssinaturasParaFaturamento;
import br.com.condomix.plataforma.MotivoDaSuspensao;
import br.com.condomix.plataforma.Periodicidade;
import br.com.condomix.plataforma.StatusAssinatura;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A assinatura do Condomix vista pela gestão do condomínio: o plano e as faturas, com a 2ª via.
 * Estas são as únicas rotas liberadas quando o condomínio está suspenso por falta de pagamento.
 */
@Tag(name = "Assinatura do Condomix")
@RestController
@RequestMapping("/api/assinatura")
@PreAuthorize(Permissoes.GESTAO)
class AssinaturaController {

	private final AssinaturasParaFaturamento assinaturas;

	private final ConsultaDeFaturas faturas;

	private final Clock relogio;

	AssinaturaController(AssinaturasParaFaturamento assinaturas, ConsultaDeFaturas faturas, Clock relogio) {
		this.assinaturas = assinaturas;
		this.faturas = faturas;
		this.relogio = relogio;
	}

	/**
	 * @param fimDoTeste             só no teste grátis
	 * @param preco                  do plano, na periodicidade contratada; vazio se ainda não definido
	 * @param suspensaPorFaltaDePagamento o acesso está suspenso: só esta tela funciona até pagar
	 */
	record AssinaturaDoCondominio(String plano, Periodicidade periodicidade, StatusAssinatura status, LocalDate fimDoTeste,
			BigDecimal preco, boolean suspensaPorFaltaDePagamento, List<FaturaDaAssinatura> faturas) {
	}

	/** @param suspensaoEm quando o acesso é suspenso se não pagar; vazio se não está esperando pagamento */
	record FaturaDaAssinatura(UUID id, LocalDate periodoInicio, LocalDate periodoFim, BigDecimal valor,
			LocalDate vencimento, SituacaoDaFatura situacao, boolean atrasada, LocalDate suspensaoEm, LocalDate pagoEm) {
	}

	@Operation(summary = "O plano do condomínio e as faturas da assinatura do Condomix")
	@GetMapping
	AssinaturaDoCondominio assinatura() {
		UUID condominio = TenantAtual.obrigatorio();
		var assinatura = assinaturas.doCondominio(condominio)
				.orElseThrow(() -> new NaoEncontradoException("Este condomínio não tem assinatura"));
		LocalDate hoje = LocalDate.now(relogio);
		var lista = faturas.doCondominioAtual().stream()
				.filter(f -> f.getSituacao() != SituacaoDaFatura.CANCELADA && f.getSituacao() != SituacaoDaFatura.A_CANCELAR)
				.map(f -> new FaturaDaAssinatura(f.getId(), f.getPeriodoInicio(), f.getPeriodoFim(), f.getValor(),
						f.getVencimento(), f.getSituacao(), f.atrasadaEm(hoje), f.suspensaoEm(), f.getPagoEm()))
				.toList();
		return new AssinaturaDoCondominio(assinatura.nomeDoPlano(), assinatura.periodicidade(), assinatura.status(),
				assinatura.fimDoTeste(), assinatura.preco(),
				!assinatura.ativo() && assinatura.motivoSuspensao() == MotivoDaSuspensao.FALTA_DE_PAGAMENTO,
				lista);
	}

	@Operation(summary = "2ª via da fatura: Pix e boleto")
	@GetMapping("/faturas/{id}/pagamento")
	PagamentoDaFatura pagamento(@PathVariable UUID id) {
		return faturas.segundaViaNoCondominioAtual(id);
	}

}
