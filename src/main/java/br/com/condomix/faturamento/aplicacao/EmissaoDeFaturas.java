package br.com.condomix.faturamento.aplicacao;

import br.com.condomix.compartilhado.FormatoBrasileiro;
import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.faturamento.SituacaoDaFatura;
import br.com.condomix.faturamento.dominio.Fatura;
import br.com.condomix.faturamento.persistencia.FaturaRepository;
import br.com.condomix.pagamentos.FalhaNoGateway;
import br.com.condomix.pagamentos.GatewayDePagamento.BoletoEmitido;
import br.com.condomix.pagamentos.GatewayDePagamento.Pagador;
import br.com.condomix.pagamentos.GatewayDePagamento.PedidoDeBoleto;
import br.com.condomix.plataforma.AssinaturasParaFaturamento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Fala com o gateway: emite o boleto das faturas geradas e cancela as perdoadas. Roda a cada poucos segundos.
 * Cada fatura: lê o necessário numa transação, chama o gateway fora dela e grava o resultado em outra.
 */
@Component
public class EmissaoDeFaturas {

	private static final Logger log = LoggerFactory.getLogger(EmissaoDeFaturas.class);

	private static final int LOTE = 50;

	private final FaturaRepository faturas;

	private final AssinaturasParaFaturamento assinaturas;

	private final ContaDeRecebimentoService conta;

	private final TransactionTemplate transacao;

	private final Clock relogio;

	private final PropriedadesDoFaturamento propriedades;

	public EmissaoDeFaturas(FaturaRepository faturas, AssinaturasParaFaturamento assinaturas,
			ContaDeRecebimentoService conta, TransactionTemplate transacao, Clock relogio,
			PropriedadesDoFaturamento propriedades) {
		this.faturas = faturas;
		this.assinaturas = assinaturas;
		this.conta = conta;
		this.transacao = transacao;
		this.relogio = relogio;
		this.propriedades = propriedades;
	}

	@Scheduled(fixedDelayString = "${condomix.faturamento.intervalo-da-emissao:10s}",
			initialDelayString = "${condomix.faturamento.intervalo-da-emissao:10s}")
	void agendada() {
		if (propriedades.automatico()) {
			processar();
		}
	}

	public void processar() {
		var acesso = conta.configurada().orElse(null);
		if (acesso == null) {
			return;
		}
		boolean podeEmitir = conta.ativa().isPresent();
		List<UUID> condominios = TenantAtual.comoSistema(() -> transacao.execute(s -> faturas.condominiosComFaturasAProcessar()));
		for (UUID condominio : condominios) {
			try {
				TenantAtual.executarComo(condominio, () -> {
					processarCondominioAtual(acesso, podeEmitir);
					return null;
				});
			}
			catch (RuntimeException e) {
				log.warn("Falha ao processar as faturas do condomínio {}", condominio, e);
			}
		}
	}

	private void processarCondominioAtual(ContaDeRecebimentoService.AcessoAoRecebimento acesso, boolean podeEmitir) {
		transacao.execute(s -> ids(SituacaoDaFatura.A_CANCELAR)).forEach(id -> cancelar(id, acesso));
		if (podeEmitir) {
			transacao.execute(s -> ids(SituacaoDaFatura.A_EMITIR)).forEach(id -> emitir(id, acesso));
		}
	}

	private List<UUID> ids(SituacaoDaFatura situacao) {
		return faturas.findBySituacaoOrderByCriadoEm(situacao, Limit.of(LOTE)).stream().map(Fatura::getId).toList();
	}

	private void emitir(UUID faturaId, ContaDeRecebimentoService.AcessoAoRecebimento acesso) {
		PreparoDaFatura preparo = transacao.execute(s -> preparar(faturaId));
		if (preparo == null) {
			return;
		}
		if (preparo.problema() != null) {
			gravar(faturaId, f -> f.falhou(preparo.problema()));
			return;
		}
		try {
			BoletoEmitido emitido = acesso.gateway().emitir(acesso.credencial(), preparo.pedido());
			gravar(faturaId, f -> f.emitida(acesso.gateway().tipo(), emitido, preparo.pedido().vencimento()));
		}
		catch (FalhaNoGateway e) {
			gravar(faturaId, f -> f.falhou(e.getMessage()));
		}
		catch (RuntimeException e) {
			log.warn("Erro inesperado ao emitir a fatura {}", faturaId, e);
			gravar(faturaId, f -> f.falhou("Não foi possível falar com o gateway. Tente de novo em alguns minutos."));
		}
	}

	private void cancelar(UUID faturaId, ContaDeRecebimentoService.AcessoAoRecebimento acesso) {
		String idNoGateway = transacao.execute(s -> faturas.findById(faturaId).map(Fatura::getIdNoGateway).orElse(null));
		try {
			acesso.gateway().cancelar(acesso.credencial(), idNoGateway);
			gravar(faturaId, Fatura::canceladaNoGateway);
		}
		catch (RuntimeException e) {
			// Fica A_CANCELAR e é tentada de novo na próxima rodada.
			log.warn("Não foi possível cancelar a fatura {} no gateway", faturaId, e);
		}
	}

	/** O pedido para o gateway, ou o motivo de não dar para emitir. Nulo se a fatura não está mais a emitir. */
	private PreparoDaFatura preparar(UUID faturaId) {
		Fatura fatura = faturas.findById(faturaId).filter(f -> f.getSituacao() == SituacaoDaFatura.A_EMITIR).orElse(null);
		if (fatura == null) {
			return null;
		}
		var pagador = assinaturas.pagador(fatura.getTenantId());
		if (pagador.cnpj() == null || pagador.cnpj().isBlank()) {
			return new PreparoDaFatura(null, "Falta o CNPJ do condomínio, que o banco exige no boleto. Cadastre na página do cliente,"
					+ " em \"Dados para a fatura\", e tente de novo.");
		}
		// O gateway não aceita vencimento no passado: emitida com atraso, a fatura vence hoje.
		LocalDate hoje = LocalDate.now(relogio);
		LocalDate vencimento = fatura.getVencimento().isBefore(hoje) ? hoje : fatura.getVencimento();
		String descricao = "Assinatura Condomix, plano " + fatura.getNomeDoPlano() + ", de "
				+ FormatoBrasileiro.data(fatura.getPeriodoInicio()) + " a " + FormatoBrasileiro.data(fatura.getPeriodoFim().minusDays(1));
		return new PreparoDaFatura(new PedidoDeBoleto(fatura.getId(),
				new Pagador(pagador.condominioId(), pagador.nome(), pagador.cnpj(), pagador.email()),
				fatura.getValor(), vencimento, descricao, propriedades.multaPercentual(), propriedades.jurosMensalPercentual()), null);
	}

	private void gravar(UUID faturaId, Consumer<Fatura> mudanca) {
		transacao.executeWithoutResult(s -> faturas.findById(faturaId).ifPresent(mudanca));
	}

	private record PreparoDaFatura(PedidoDeBoleto pedido, String problema) {
	}

}
