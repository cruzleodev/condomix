package br.com.condomix.financeiro.aplicacao;

import br.com.condomix.compartilhado.Cofre;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.pagamentos.AmbienteDoGateway;
import br.com.condomix.pagamentos.TipoDeGateway;
import br.com.condomix.financeiro.dominio.ContaNoGateway;
import br.com.condomix.pagamentos.FalhaNoGateway;
import br.com.condomix.pagamentos.GatewayDePagamento;
import br.com.condomix.pagamentos.GatewayDePagamento.Credencial;
import br.com.condomix.pagamentos.Gateways;
import br.com.condomix.financeiro.persistencia.ContaNoGatewayRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * A conta do condomínio atual no gateway: configurar, desativar e obter a credencial para as chamadas.
 * Sem {@code @Transactional} na classe: validar a chave é uma chamada pela rede, feita fora da transação.
 */
@Service
public class ContaDoGateway {

	private final ContaNoGatewayRepository contas;

	private final Cofre cofre;

	private final Gateways gateways;

	private final TransactionTemplate transacao;

	private final Clock relogio;

	private final String enderecoPublico;

	public ContaDoGateway(ContaNoGatewayRepository contas, Cofre cofre, Gateways gateways, TransactionTemplate transacao,
			Clock relogio, @Value("${condomix.gateway.endereco-publico:http://localhost:8080}") String enderecoPublico) {
		this.contas = contas;
		this.cofre = cofre;
		this.gateways = gateways;
		this.transacao = transacao;
		this.relogio = relogio;
		this.enderecoPublico = enderecoPublico.replaceAll("/+$", "");
	}

	/** O gateway e a credencial do condomínio, para chamar a API dele. */
	public record Acesso(GatewayDePagamento gateway, Credencial credencial) {
	}

	/** O que o administrador cola na configuração de avisos (webhook) do gateway. O token só aparece agora. */
	public record AvisoDoGateway(String url, String token) {
	}

	/** Para emitir boletos novos: só com a conta ativa. */
	public Optional<Acesso> ativa() {
		return lerConta().filter(ContaNoGateway::isAtiva).map(this::acesso);
	}

	/** Para cancelar e confirmar boletos já emitidos: vale mesmo com a conta desativada. */
	public Optional<Acesso> configurada() {
		return lerConta().map(this::acesso);
	}

	public Optional<ContaNoGateway> situacao() {
		return lerConta();
	}

	public boolean avisoConfere(String token) {
		return lerConta().map(c -> Cofre.confere(token, c.getTokenAvisoHash())).orElse(false);
	}

	public AvisoDoGateway configurar(TipoDeGateway tipo, AmbienteDoGateway ambiente, String chave, UUID por) {
		if (chave == null || chave.isBlank()) {
			throw RegraDeNegocioException.invalido("Informe a chave de API da conta do condomínio no gateway");
		}
		Credencial credencial = new Credencial(ambiente, chave.strip());
		try {
			gateways.de(tipo).validar(credencial);
		}
		catch (FalhaNoGateway e) {
			throw RegraDeNegocioException.invalido(e.getMessage());
		}
		String token = Cofre.tokenAleatorio();
		UUID condominio = TenantAtual.obrigatorio();
		transacao.executeWithoutResult(s -> {
			ContaNoGateway conta = contas.doCondominio().orElseGet(() -> new ContaNoGateway(condominio));
			conta.configurar(tipo, ambiente, cofre.cifrar(credencial.chave()), Cofre.impressaoDigital(token),
					Instant.now(relogio), por);
			contas.save(conta);
		});
		return new AvisoDoGateway(urlDoAviso(tipo, condominio), token);
	}

	public void desativar() {
		transacao.executeWithoutResult(s -> contas.doCondominio()
				.orElseThrow(() -> RegraDeNegocioException.conflito("Este condomínio não tem gateway configurado"))
				.desativar());
	}

	private String urlDoAviso(TipoDeGateway tipo, UUID condominio) {
		return enderecoPublico + "/api/avisos/" + tipo.name().toLowerCase(Locale.ROOT) + "/" + condominio;
	}

	private Optional<ContaNoGateway> lerConta() {
		return transacao.execute(s -> contas.doCondominio());
	}

	private Acesso acesso(ContaNoGateway conta) {
		return new Acesso(gateways.de(conta.getGateway()), new Credencial(conta.getAmbiente(), cofre.decifrar(conta.getChaveCifrada())));
	}

}
