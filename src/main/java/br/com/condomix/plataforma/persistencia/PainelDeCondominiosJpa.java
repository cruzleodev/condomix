package br.com.condomix.plataforma.persistencia;

import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.plataforma.Assinatura;
import br.com.condomix.plataforma.Condominio;
import br.com.condomix.plataforma.PainelDeCondominios;
import br.com.condomix.plataforma.Periodicidade;
import br.com.condomix.plataforma.Plano;
import br.com.condomix.plataforma.StatusAssinatura;
import br.com.condomix.plataforma.TipoDeEventoDaAssinatura;
import br.com.condomix.plataforma.TipoPlano;
import br.com.condomix.plataforma.historico.EventoDeAssinatura;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sem {@code @Transactional} na classe: a lista lê assinaturas de todos os condomínios (modo sistema)
 * e a alteração roda dentro do condomínio; nos dois casos o tenant é definido antes da transação.
 */
@Component
class PainelDeCondominiosJpa implements PainelDeCondominios {

	private final CondominioRepository condominios;

	private final AssinaturaRepository assinaturas;

	private final PlanoRepository planos;

	private final EventoDeAssinaturaRepository historico;

	private final TransactionTemplate transacao;

	private final Clock relogio;

	PainelDeCondominiosJpa(CondominioRepository condominios, AssinaturaRepository assinaturas, PlanoRepository planos,
			EventoDeAssinaturaRepository historico, TransactionTemplate transacao, Clock relogio) {
		this.condominios = condominios;
		this.assinaturas = assinaturas;
		this.planos = planos;
		this.historico = historico;
		this.transacao = transacao;
		this.relogio = relogio;
	}

	@Override
	public List<CondominioNaPlataforma> listar() {
		Map<UUID, Assinatura> vigentes = TenantAtual.comoSistema(() -> assinaturas.vigentesDePlanoBase()).stream()
				.collect(Collectors.toMap(Assinatura::getTenantId, Function.identity(), (a, b) -> a));
		return condominios.findAll().stream()
				.sorted(Comparator.comparing(Condominio::getNome, String.CASE_INSENSITIVE_ORDER))
				.map(c -> paraPainel(c, vigentes.get(c.getId())))
				.toList();
	}

	@Override
	public CondominioNaPlataforma obter(UUID condominioId) {
		Condominio condominio = buscar(condominioId);
		Assinatura vigente = TenantAtual.executarComo(condominioId, () -> assinaturas.vigentesDePlanoBase())
				.stream().findFirst().orElse(null);
		return paraPainel(condominio, vigente);
	}

	@Override
	public void suspender(UUID condominioId, UUID feitoPor) {
		transacao.executeWithoutResult(s -> {
			Condominio condominio = buscar(condominioId);
			if (condominio.isAtivo()) {
				condominio.desativar();
				historico.save(EventoDeAssinatura.doAcesso(condominioId, Instant.now(relogio), true, feitoPor));
			}
		});
	}

	@Override
	public void reativar(UUID condominioId, UUID feitoPor) {
		transacao.executeWithoutResult(s -> {
			Condominio condominio = buscar(condominioId);
			if (!condominio.isAtivo()) {
				condominio.reativar();
				historico.save(EventoDeAssinatura.doAcesso(condominioId, Instant.now(relogio), false, feitoPor));
			}
		});
	}

	@Override
	public List<EventoDaAssinatura> historico(UUID condominioId) {
		buscar(condominioId);
		return transacao.execute(s -> historico.doCondominio(condominioId).stream()
				.map(e -> new EventoDaAssinatura(e.getMomento(), e.getTipo(),
						e.getPlano() == null ? null : e.getPlano().getCodigo(),
						e.getPlano() == null ? null : e.getPlano().getNome(),
						e.getPeriodicidade(), e.getStatus(), e.getFeitoPor()))
				.toList());
	}

	@Override
	public CondominioNaPlataforma alterarAssinatura(UUID condominioId, String codigoPlano, Periodicidade periodicidade,
			StatusAssinatura status, UUID feitoPor) {
		if (status == StatusAssinatura.CANCELADA) {
			throw RegraDeNegocioException.invalido("Para encerrar o cliente, suspenda o condomínio");
		}
		Condominio condominio = buscar(condominioId);
		TenantAtual.executarComo(condominioId, () -> {
			transacao.executeWithoutResult(s -> {
				Plano plano = planos.findByCodigo(codigoPlano)
						.filter(p -> p.isAtivo() && p.getTipo() == TipoPlano.BASE)
						.orElseThrow(() -> RegraDeNegocioException.invalido("Plano base inexistente: " + codigoPlano));
				LocalDate hoje = LocalDate.now(relogio);
				var atual = assinaturas.vigentesDePlanoBase().stream().findFirst();
				boolean mesmaAssinatura = atual.isPresent() && atual.get().getPlano().getCodigo().equals(codigoPlano)
						&& atual.get().getPeriodicidade() == periodicidade;
				if (mesmaAssinatura) {
					if (atual.get().getStatus() != status) {
						atual.get().alterarStatus(status);
						registrar(condominioId, TipoDeEventoDaAssinatura.MUDANCA_DE_SITUACAO, plano, periodicidade, status, feitoPor);
					}
					return;
				}
				atual.ifPresent(a -> a.cancelar(hoje));
				assinaturas.flush(); // grava o cancelamento antes da nova (regra de um plano vigente por vez)
				assinaturas.save(new Assinatura(condominio, plano, periodicidade, status, hoje));
				registrar(condominioId, TipoDeEventoDaAssinatura.TROCA_DE_PLANO, plano, periodicidade, status, feitoPor);
			});
			return null;
		});
		return obter(condominioId);
	}

	private void registrar(UUID condominioId, TipoDeEventoDaAssinatura tipo, Plano plano, Periodicidade periodicidade,
			StatusAssinatura status, UUID feitoPor) {
		historico.save(EventoDeAssinatura.daAssinatura(condominioId, Instant.now(relogio), tipo, plano, periodicidade,
				status, feitoPor));
	}

	private Condominio buscar(UUID condominioId) {
		return condominios.findById(condominioId).orElseThrow(() -> new NaoEncontradoException("Condomínio não encontrado"));
	}

	private static CondominioNaPlataforma paraPainel(Condominio c, Assinatura a) {
		AssinaturaAtual assinatura = a == null ? null : new AssinaturaAtual(a.getPlano().getCodigo(),
				a.getPlano().getNome(), a.getPeriodicidade(), a.getStatus(), a.getInicio());
		return new CondominioNaPlataforma(c.getId(), c.getNome(), c.getTipo(), c.getCnpj(), c.isAtivo(),
				c.getCriadoEm(), assinatura);
	}

}
