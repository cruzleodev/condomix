package br.com.condomix.administracao;

import br.com.condomix.acesso.LoginsDaPlataforma;
import br.com.condomix.plataforma.PainelDeCondominios;
import br.com.condomix.plataforma.PainelDeCondominios.EventoDaAssinatura;
import br.com.condomix.plataforma.Periodicidade;
import br.com.condomix.plataforma.StatusAssinatura;
import br.com.condomix.plataforma.TipoDeEventoDaAssinatura;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Junta o histórico da assinatura (módulo plataforma) com o e-mail de quem fez cada mudança (módulo acesso). */
@Service
class HistoricoDaAssinatura {

	private final PainelDeCondominios painel;

	private final LoginsDaPlataforma logins;

	HistoricoDaAssinatura(PainelDeCondominios painel, LoginsDaPlataforma logins) {
		this.painel = painel;
		this.logins = logins;
	}

	/** {@code feitoPor} vazio: feito pelo sistema (ou antes de o histórico existir). */
	record AlteracaoDaAssinatura(Instant momento, TipoDeEventoDaAssinatura tipo, String plano, String nomeDoPlano,
			Periodicidade periodicidade, StatusAssinatura status, LocalDate fimDoTeste, String feitoPor) {
	}

	List<AlteracaoDaAssinatura> doCondominio(UUID condominioId) {
		List<EventoDaAssinatura> eventos = painel.historico(condominioId);
		Map<UUID, String> emails = logins.emails(
				eventos.stream().map(EventoDaAssinatura::feitoPor).filter(Objects::nonNull).distinct().toList());
		return eventos.stream()
				.map(e -> new AlteracaoDaAssinatura(e.momento(), e.tipo(), e.plano(), e.nomeDoPlano(), e.periodicidade(),
						e.status(), e.fimDoTeste(), e.feitoPor() == null ? null : emails.get(e.feitoPor())))
				.toList();
	}

}
