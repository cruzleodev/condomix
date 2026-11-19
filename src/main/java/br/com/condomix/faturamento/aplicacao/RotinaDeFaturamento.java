package br.com.condomix.faturamento.aplicacao;

import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.faturamento.AcessoSuspensoPorFaltaDePagamento;
import br.com.condomix.faturamento.FaturasDosClientes.ResumoDaRotina;
import br.com.condomix.faturamento.dominio.CalendarioDeFaturas;
import br.com.condomix.faturamento.dominio.Fatura;
import br.com.condomix.faturamento.persistencia.FaturaRepository;
import br.com.condomix.plataforma.AssinaturasParaFaturamento;
import br.com.condomix.plataforma.AssinaturasParaFaturamento.AssinaturaCobravel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A rotina da cobrança das assinaturas, de hora em hora (repetir no mesmo dia não faz nada de novo):
 * <ol>
 *   <li>os testes grátis que acabaram viram assinaturas ativas;</li>
 *   <li>cada assinatura ativa ganha a fatura do próximo período, {@value CalendarioDeFaturas#ANTECEDENCIA} dias antes;</li>
 *   <li>quem passou {@value CalendarioDeFaturas#TOLERANCIA} dias do vencimento sem pagar tem o acesso suspenso.</li>
 * </ol>
 * O boleto de cada fatura nova é emitido pela {@link EmissaoDeFaturas}, fora daqui.
 */
@Component
public class RotinaDeFaturamento {

	private static final Logger log = LoggerFactory.getLogger(RotinaDeFaturamento.class);

	private final AssinaturasParaFaturamento assinaturas;

	private final FaturaRepository faturas;

	private final ContaDeRecebimentoService conta;

	private final TransactionTemplate transacao;

	private final Clock relogio;

	private final PropriedadesDoFaturamento propriedades;

	private final ApplicationEventPublisher eventos;

	public RotinaDeFaturamento(AssinaturasParaFaturamento assinaturas, FaturaRepository faturas,
			ContaDeRecebimentoService conta, TransactionTemplate transacao, Clock relogio,
			PropriedadesDoFaturamento propriedades, ApplicationEventPublisher eventos) {
		this.eventos = eventos;
		this.assinaturas = assinaturas;
		this.faturas = faturas;
		this.conta = conta;
		this.transacao = transacao;
		this.relogio = relogio;
		this.propriedades = propriedades;
	}

	@Scheduled(fixedDelayString = "${condomix.faturamento.intervalo-da-rotina:1h}", initialDelayString = "30s")
	void agendada() {
		if (propriedades.automatico()) {
			executar();
		}
	}

	public ResumoDaRotina executar() {
		LocalDate hoje = LocalDate.now(relogio);
		int testes = assinaturas.encerrarTestesVencidos(hoje);
		// Sem a conta do Condomix ligada, não há como cobrar: nenhuma fatura é gerada (nem suspensão por atraso).
		int geradas = conta.ativa().isPresent() ? gerarFaturas(hoje) : 0;
		int suspensos = suspenderAtrasados(hoje);
		if (testes + geradas + suspensos > 0) {
			log.info("Rotina de faturamento: {} teste(s) encerrado(s), {} fatura(s) gerada(s), {} condomínio(s) suspenso(s)",
					testes, geradas, suspensos);
		}
		return new ResumoDaRotina(testes, geradas, suspensos);
	}

	private int gerarFaturas(LocalDate hoje) {
		int geradas = 0;
		for (AssinaturaCobravel assinatura : assinaturas.cobraveis()) {
			if (assinatura.preco() == null || assinatura.preco().signum() <= 0) {
				log.warn("O plano {} ({}) do condomínio {} não tem preço: a fatura não foi gerada",
						assinatura.nomeDoPlano(), assinatura.periodicidade(), assinatura.condominioId());
				continue;
			}
			try {
				Boolean gerou = TenantAtual.executarComo(assinatura.condominioId(), () -> transacao.execute(s -> gerar(assinatura, hoje)));
				geradas += Boolean.TRUE.equals(gerou) ? 1 : 0;
			}
			catch (DataIntegrityViolationException e) {
				log.info("A fatura do condomínio {} já tinha sido gerada (rotina em paralelo)", assinatura.condominioId());
			}
		}
		return geradas;
	}

	private boolean gerar(AssinaturaCobravel assinatura, LocalDate hoje) {
		LocalDate fimDaUltima = faturas.fimDaUltima().orElse(null);
		return CalendarioDeFaturas.proximo(fimDaUltima, hoje, assinatura.periodicidade())
				.map(periodo -> faturas.save(new Fatura(assinatura.condominioId(), assinatura.planoId(),
						assinatura.nomeDoPlano(), assinatura.periodicidade(), periodo, assinatura.preco())))
				.isPresent();
	}

	private int suspenderAtrasados(LocalDate hoje) {
		List<UUID> atrasados = TenantAtual.comoSistema(() -> transacao.execute(
				s -> faturas.condominiosComFaturaVencidaAntesDe(CalendarioDeFaturas.limiteDaTolerancia(hoje))));
		int suspensos = 0;
		for (UUID condominio : atrasados) {
			if (assinaturas.suspenderPorFaltaDePagamento(condominio)) {
				transacao.executeWithoutResult(s -> eventos.publishEvent(new AcessoSuspensoPorFaltaDePagamento(condominio)));
				suspensos++;
			}
		}
		return suspensos;
	}

}
