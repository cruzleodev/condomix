package br.com.condomix.faturamento.aplicacao;

import br.com.condomix.compartilhado.Cofre;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.faturamento.ContaDoCondomixNoGateway;
import br.com.condomix.faturamento.dominio.ContaDeRecebimento;
import br.com.condomix.faturamento.persistencia.ContaDeRecebimentoRepository;
import br.com.condomix.pagamentos.AmbienteDoGateway;
import br.com.condomix.pagamentos.FalhaNoGateway;
import br.com.condomix.pagamentos.GatewayDePagamento;
import br.com.condomix.pagamentos.GatewayDePagamento.Credencial;
import br.com.condomix.pagamentos.Gateways;
import br.com.condomix.pagamentos.TipoDeGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A conta do Condomix no gateway: configurar, desativar e obter a credencial para as chamadas.
 * Sem {@code @Transactional} na classe: validar a chave é uma chamada pela rede, feita fora da transação.
 */
@Service
@EnableConfigurationProperties(PropriedadesDoFaturamento.class)
public class ContaDeRecebimentoService implements ContaDoCondomixNoGateway {

	private final ContaDeRecebimentoRepository contas;

	private final Cofre cofre;

	private final Gateways gateways;

	private final TransactionTemplate transacao;

	private final Clock relogio;

	private final String enderecoPublico;

	public ContaDeRecebimentoService(ContaDeRecebimentoRepository contas, Cofre cofre, Gateways gateways,
			TransactionTemplate transacao, Clock relogio,
			@Value("${condomix.gateway.endereco-publico:http://localhost:8080}") String enderecoPublico) {
		this.contas = contas;
		this.cofre = cofre;
		this.gateways = gateways;
		this.transacao = transacao;
		this.relogio = relogio;
		this.enderecoPublico = enderecoPublico.replaceAll("/+$", "");
	}

	/** O gateway e a credencial do Condomix, para chamar a API dele. */
	public record AcessoAoRecebimento(GatewayDePagamento gateway, Credencial credencial) {
	}

	/** Para gerar e emitir faturas novas: só com a conta ativa. */
	public Optional<AcessoAoRecebimento> ativa() {
		return lerConta().filter(ContaDeRecebimento::isAtiva).map(this::acesso);
	}

	/** Para cancelar e confirmar faturas já emitidas: vale mesmo com a conta desativada. */
	public Optional<AcessoAoRecebimento> configurada() {
		return lerConta().map(this::acesso);
	}

	public boolean avisoConfere(String token) {
		return lerConta().map(c -> Cofre.confere(token, c.getTokenAvisoHash())).orElse(false);
	}

	@Override
	public SituacaoDaContaDeRecebimento situacao() {
		return lerConta()
				.map(c -> new SituacaoDaContaDeRecebimento(true, c.isAtiva(), c.getGateway(), c.getAmbiente(), c.getConfiguradaEm()))
				.orElse(new SituacaoDaContaDeRecebimento(false, false, null, null, null));
	}

	@Override
	public AvisoDaContaDeRecebimento configurar(TipoDeGateway tipo, AmbienteDoGateway ambiente, String chave, UUID por) {
		if (chave == null || chave.isBlank()) {
			throw RegraDeNegocioException.invalido("Informe a chave de API da conta do Condomix no gateway");
		}
		Credencial credencial = new Credencial(ambiente, chave.strip());
		try {
			gateways.de(tipo).validar(credencial);
		}
		catch (FalhaNoGateway e) {
			throw RegraDeNegocioException.invalido(e.getMessage());
		}
		String token = Cofre.tokenAleatorio();
		String chaveCifrada = cofre.cifrar(credencial.chave());
		Instant agora = Instant.now(relogio);
		transacao.executeWithoutResult(s -> contas.daPlataforma().ifPresentOrElse(
				c -> c.configurar(tipo, ambiente, chaveCifrada, Cofre.impressaoDigital(token), agora, por),
				() -> contas.save(new ContaDeRecebimento(tipo, ambiente, chaveCifrada, Cofre.impressaoDigital(token), agora, por))));
		return new AvisoDaContaDeRecebimento(
				enderecoPublico + "/api/avisos/" + tipo.name().toLowerCase(Locale.ROOT) + "/assinaturas", token);
	}

	@Override
	public void desativar() {
		transacao.executeWithoutResult(s -> contas.daPlataforma()
				.orElseThrow(() -> RegraDeNegocioException.conflito("A conta do Condomix no gateway ainda não foi configurada"))
				.desativar());
	}

	@Override
	public Set<TipoDeGateway> disponiveis() {
		return gateways.disponiveis();
	}

	private Optional<ContaDeRecebimento> lerConta() {
		return transacao.execute(s -> contas.daPlataforma());
	}

	private AcessoAoRecebimento acesso(ContaDeRecebimento conta) {
		return new AcessoAoRecebimento(gateways.de(conta.getGateway()), new Credencial(conta.getAmbiente(), cofre.decifrar(conta.getChaveCifrada())));
	}

}
