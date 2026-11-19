package br.com.condomix.faturamento.aplicacao;

import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.faturamento.FaturasDosClientes;
import br.com.condomix.faturamento.SituacaoDaFatura;
import br.com.condomix.faturamento.dominio.Fatura;
import br.com.condomix.faturamento.persistencia.FaturaRepository;
import br.com.condomix.plataforma.PainelDeCondominios;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * As faturas para o painel da plataforma (de todos os clientes) e para o síndico (só as do condomínio atual).
 * Sem {@code @Transactional} na classe: o tenant é definido antes de cada transação.
 */
@Service
public class ConsultaDeFaturas implements FaturasDosClientes {

	private final FaturaRepository faturas;

	private final PainelDeCondominios condominios;

	private final RotinaDeFaturamento rotina;

	private final ReativacaoDoAcesso reativacao;

	private final TransactionTemplate transacao;

	private final Clock relogio;

	ConsultaDeFaturas(FaturaRepository faturas, PainelDeCondominios condominios, RotinaDeFaturamento rotina,
			ReativacaoDoAcesso reativacao, TransactionTemplate transacao, Clock relogio) {
		this.faturas = faturas;
		this.condominios = condominios;
		this.rotina = rotina;
		this.reativacao = reativacao;
		this.transacao = transacao;
		this.relogio = relogio;
	}

	@Override
	public List<FaturaDeCliente> todas() {
		Map<UUID, String> nomes = condominios.listar().stream()
				.collect(Collectors.toMap(PainelDeCondominios.CondominioNaPlataforma::id, PainelDeCondominios.CondominioNaPlataforma::nome));
		return TenantAtual.comoSistema(() -> transacao.execute(s -> faturas.findAllByOrderByPeriodoInicioDescCriadoEmDesc()))
				.stream().map(f -> paraPainel(f, nomes.get(f.getTenantId()))).toList();
	}

	@Override
	public List<FaturaDeCliente> doCliente(UUID condominioId) {
		String nome = condominios.obter(condominioId).nome();
		return TenantAtual.executarComo(condominioId, () -> transacao.execute(s -> faturas.findAllByOrderByPeriodoInicioDescCriadoEmDesc()))
				.stream().map(f -> paraPainel(f, nome)).toList();
	}

	@Override
	public PagamentoDaFatura segundaVia(UUID faturaId) {
		return paraPagamento(TenantAtual.comoSistema(() -> transacao.execute(s -> buscar(faturaId))));
	}

	@Override
	public void tentarDeNovo(UUID faturaId) {
		mudar(faturaId, f -> {
			if (f.getSituacao() != SituacaoDaFatura.FALHOU) {
				throw RegraDeNegocioException.conflito("Só a fatura com problema pode ser emitida de novo");
			}
			f.tentarDeNovo();
		});
	}

	@Override
	public void cancelar(UUID faturaId, UUID administradorId) {
		UUID condominio = mudar(faturaId, f -> {
			if (f.getSituacao() == SituacaoDaFatura.PAGA) {
				throw RegraDeNegocioException.conflito("Esta fatura já foi paga e não pode ser cancelada");
			}
			f.cancelar(Instant.now(relogio), administradorId);
		});
		reativacao.verificar(condominio);
	}

	@Override
	public ResumoDaRotina rodarRotinaAgora() {
		return rotina.executar();
	}

	/** Só do condomínio atual (o síndico). */
	public List<Fatura> doCondominioAtual() {
		return transacao.execute(s -> faturas.findAllByOrderByPeriodoInicioDescCriadoEmDesc());
	}

	/** 2ª via de uma fatura do condomínio atual (o síndico). */
	public PagamentoDaFatura segundaViaNoCondominioAtual(UUID faturaId) {
		return paraPagamento(transacao.execute(s -> buscar(faturaId)));
	}

	/** Para o botão "simular pagamento": o boleto da fatura no gateway. */
	public Fatura daPlataforma(UUID faturaId) {
		return TenantAtual.comoSistema(() -> transacao.execute(s -> buscar(faturaId)));
	}

	/** Muda uma fatura de qualquer condomínio, dentro dele. Devolve o condomínio. */
	private UUID mudar(UUID faturaId, Consumer<Fatura> mudanca) {
		UUID condominio = daPlataforma(faturaId).getTenantId();
		TenantAtual.executarComo(condominio, () -> {
			transacao.executeWithoutResult(s -> mudanca.accept(buscar(faturaId)));
			return null;
		});
		return condominio;
	}

	private Fatura buscar(UUID faturaId) {
		return faturas.findById(faturaId).orElseThrow(() -> new NaoEncontradoException("Fatura não encontrada"));
	}

	private PagamentoDaFatura paraPagamento(Fatura f) {
		if (f.getSituacao() != SituacaoDaFatura.EMITIDA) {
			throw RegraDeNegocioException.conflito(Optional.ofNullable(MOTIVO_SEM_2A_VIA.get(f.getSituacao()))
					.orElse("Esta fatura não está esperando pagamento"));
		}
		return new PagamentoDaFatura(f.getValor(), f.getVencimento(), f.getGateway(), f.getUrl(), f.getLinhaDigitavel(),
				f.getPixCopiaECola(), f.getPixQrCode());
	}

	private static final Map<SituacaoDaFatura, String> MOTIVO_SEM_2A_VIA = Map.of(
			SituacaoDaFatura.A_EMITIR, "O boleto desta fatura está sendo gerado. Tente de novo em alguns segundos.",
			SituacaoDaFatura.FALHOU, "O boleto desta fatura não pôde ser gerado. Fale com o suporte do Condomix.",
			SituacaoDaFatura.PAGA, "Esta fatura já foi paga.");

	private FaturaDeCliente paraPainel(Fatura f, String condominio) {
		LocalDate hoje = LocalDate.now(relogio);
		return new FaturaDeCliente(f.getId(), f.getTenantId(), condominio, f.getNomeDoPlano(), f.getPeriodicidade(),
				f.getPeriodoInicio(), f.getPeriodoFim(), f.getValor(), f.getVencimento(), f.getSituacao(), f.atrasadaEm(hoje),
				f.suspensaoEm(), f.getErro(), f.getGateway(), f.getPagoEm(), f.getValorPago(), f.getForma(), f.getTarifa());
	}

}
