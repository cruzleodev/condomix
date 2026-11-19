package br.com.condomix.plataforma.persistencia;

import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.plataforma.Assinatura;
import br.com.condomix.plataforma.AssinaturasParaFaturamento;
import br.com.condomix.plataforma.Condominio;
import br.com.condomix.plataforma.MotivoDaSuspensao;
import br.com.condomix.plataforma.StatusAssinatura;
import br.com.condomix.plataforma.TipoDeEventoDaAssinatura;
import br.com.condomix.plataforma.historico.EventoDeAssinatura;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sem {@code @Transactional} na classe: as leituras de todos os condomínios rodam em modo sistema e as mudanças
 * dentro de cada condomínio; nos dois casos o tenant é definido antes da transação.
 */
@Component
class AssinaturasParaFaturamentoJpa implements AssinaturasParaFaturamento {

	private final CondominioRepository condominios;

	private final AssinaturaRepository assinaturas;

	private final EventoDeAssinaturaRepository historico;

	private final TransactionTemplate transacao;

	private final Clock relogio;

	AssinaturasParaFaturamentoJpa(CondominioRepository condominios, AssinaturaRepository assinaturas,
			EventoDeAssinaturaRepository historico, TransactionTemplate transacao, Clock relogio) {
		this.condominios = condominios;
		this.assinaturas = assinaturas;
		this.historico = historico;
		this.transacao = transacao;
		this.relogio = relogio;
	}

	@Override
	public int encerrarTestesVencidos(LocalDate hoje) {
		List<UUID> vencidos = TenantAtual.comoSistema(() -> transacao.execute(s -> assinaturas.testesVencidos(hoje)
				.stream().map(Assinatura::getTenantId).toList()));
		int encerrados = 0;
		for (UUID condominio : vencidos) {
			Boolean encerrou = TenantAtual.executarComo(condominio, () -> transacao.execute(s -> {
				Assinatura assinatura = assinaturas.vigentesDePlanoBase().stream().findFirst().orElse(null);
				if (assinatura == null || !assinatura.encerrarTesteSeVenceu(hoje)) {
					return false;
				}
				historico.save(EventoDeAssinatura.daAssinatura(condominio, Instant.now(relogio),
						TipoDeEventoDaAssinatura.FIM_DO_TESTE, assinatura, null));
				return true;
			}));
			encerrados += Boolean.TRUE.equals(encerrou) ? 1 : 0;
		}
		return encerrados;
	}

	@Override
	public List<AssinaturaCobravel> cobraveis() {
		return TenantAtual.comoSistema(() -> transacao.execute(s -> {
			Map<UUID, Condominio> ativos = condominios.findAll().stream().filter(Condominio::isAtivo)
					.collect(Collectors.toMap(Condominio::getId, Function.identity()));
			return assinaturas.vigentesDePlanoBase().stream()
					.filter(a -> a.getStatus() == StatusAssinatura.ATIVA && ativos.containsKey(a.getTenantId()))
					.map(a -> new AssinaturaCobravel(a.getTenantId(), a.getPlano().getId(), a.getPlano().getNome(),
							a.getPeriodicidade(), a.getPeriodicidade().precoDe(a.getPlano())))
					.toList();
		}));
	}

	@Override
	public Optional<AssinaturaDoCliente> doCondominio(UUID condominioId) {
		Condominio condominio = buscar(condominioId);
		return TenantAtual.executarComo(condominioId, () -> transacao.execute(s -> assinaturas.vigentesDePlanoBase()
				.stream().findFirst()
				.map(a -> new AssinaturaDoCliente(a.getPlano().getNome(), a.getPeriodicidade(), a.getStatus(),
						a.getFimDoTeste(), a.getPeriodicidade().precoDe(a.getPlano()), condominio.isAtivo(),
						condominio.getMotivoSuspensao()))));
	}

	@Override
	public PagadorDaAssinatura pagador(UUID condominioId) {
		Condominio c = buscar(condominioId);
		return new PagadorDaAssinatura(c.getId(), c.getNome(), c.getCnpj(), c.getEmail());
	}

	@Override
	public void suspenderPorFaltaDePagamento(UUID condominioId) {
		transacao.executeWithoutResult(s -> {
			Condominio condominio = buscar(condominioId);
			if (condominio.isAtivo()) {
				condominio.suspender(MotivoDaSuspensao.FALTA_DE_PAGAMENTO);
				historico.save(EventoDeAssinatura.doAcesso(condominioId, Instant.now(relogio),
						TipoDeEventoDaAssinatura.SUSPENSAO_POR_FALTA_DE_PAGAMENTO, null));
			}
		});
	}

	@Override
	public void reativarAposPagamento(UUID condominioId) {
		transacao.executeWithoutResult(s -> {
			Condominio condominio = buscar(condominioId);
			if (condominio.isSuspensoPorFaltaDePagamento()) {
				condominio.reativar();
				historico.save(EventoDeAssinatura.doAcesso(condominioId, Instant.now(relogio),
						TipoDeEventoDaAssinatura.REATIVACAO_APOS_PAGAMENTO, null));
			}
		});
	}

	private Condominio buscar(UUID condominioId) {
		return condominios.findById(condominioId).orElseThrow(() -> new NaoEncontradoException("Condomínio não encontrado"));
	}

}
