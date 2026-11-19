package br.com.condomix.financeiro.aplicacao;

import br.com.condomix.acesso.CadastroParaCobranca;
import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.financeiro.dominio.Boleto;
import br.com.condomix.financeiro.dominio.Cobranca;
import br.com.condomix.financeiro.dominio.SituacaoDoBoleto;
import br.com.condomix.financeiro.dominio.StatusCobranca;
import br.com.condomix.financeiro.gateway.FalhaNoGateway;
import br.com.condomix.financeiro.gateway.GatewayDePagamento.BoletoEmitido;
import br.com.condomix.financeiro.gateway.GatewayDePagamento.Pagador;
import br.com.condomix.financeiro.gateway.GatewayDePagamento.PedidoDeBoleto;
import br.com.condomix.financeiro.persistencia.BoletoRepository;
import br.com.condomix.financeiro.persistencia.CobrancaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 * Fala com o gateway: emite os boletos pedidos e cancela os que não valem mais. Roda de tempos em tempos
 * ({@code condomix.gateway.processador.intervalo}), para a geração da taxa do mês não esperar centenas de
 * chamadas pela rede. Se o servidor cair no meio, os pedidos continuam gravados e são feitos na próxima rodada.
 *
 * <p>Cada boleto: lê o necessário numa transação, chama o gateway fora dela e grava o resultado em outra.
 */
@Component
public class ProcessadorDeBoletos {

	private static final Logger log = LoggerFactory.getLogger(ProcessadorDeBoletos.class);

	/** Por condomínio, a cada rodada. */
	private static final int LOTE = 50;

	private final BoletoRepository boletos;

	private final CobrancaRepository cobrancas;

	private final CadastroParaCobranca cadastro;

	private final ContaDoGateway contas;

	private final TransactionTemplate transacao;

	private final Clock relogio;

	private final boolean ativo;

	public ProcessadorDeBoletos(BoletoRepository boletos, CobrancaRepository cobrancas, CadastroParaCobranca cadastro,
			ContaDoGateway contas, TransactionTemplate transacao, Clock relogio,
			@Value("${condomix.gateway.processador.ativo:true}") boolean ativo) {
		this.boletos = boletos;
		this.cobrancas = cobrancas;
		this.cadastro = cadastro;
		this.contas = contas;
		this.transacao = transacao;
		this.relogio = relogio;
		this.ativo = ativo;
	}

	@Scheduled(fixedDelayString = "${condomix.gateway.processador.intervalo:10s}",
			initialDelayString = "${condomix.gateway.processador.intervalo:10s}")
	void agendado() {
		if (ativo) {
			processar();
		}
	}

	/** Todos os condomínios com boletos a emitir ou a cancelar. */
	public void processar() {
		// Só os ids dos condomínios, para depois trabalhar dentro de cada um.
		List<UUID> condominios = TenantAtual.comoSistema(() -> transacao.execute(s -> boletos.condominiosComBoletosAProcessar()));
		for (UUID condominio : condominios) {
			try {
				TenantAtual.executarComo(condominio, () -> {
					processarCondominioAtual();
					return null;
				});
			}
			catch (RuntimeException e) {
				log.warn("Falha ao processar os boletos do condomínio {}", condominio, e);
			}
		}
	}

	private void processarCondominioAtual() {
		var acesso = contas.configurada().orElse(null);
		if (acesso == null) {
			return;
		}
		List<UUID> aCancelar = transacao.execute(s -> boletos.findBySituacao(SituacaoDoBoleto.A_CANCELAR).stream().map(Boleto::getId).toList());
		aCancelar.forEach(id -> cancelar(id, acesso));
		if (contas.ativa().isEmpty()) {
			return; // conta desativada: os pedidos esperam até ser ligada de novo
		}
		List<UUID> pendentes = transacao.execute(s -> boletos
				.findBySituacaoOrderByCriadoEm(SituacaoDoBoleto.PENDENTE, Limit.of(LOTE)).stream().map(Boleto::getId).toList());
		pendentes.forEach(id -> emitir(id, acesso));
	}

	private void emitir(UUID boletoId, ContaDoGateway.Acesso acesso) {
		Preparo preparo = transacao.execute(s -> preparar(boletoId));
		if (preparo == null) {
			return;
		}
		if (preparo.problema() != null) {
			gravar(boletoId, b -> b.falhou(preparo.problema()));
			return;
		}
		try {
			BoletoEmitido emitido = acesso.gateway().emitir(acesso.credencial(), preparo.pedido());
			gravar(boletoId, b -> {
				b.emitido(emitido, preparo.pedido().valor(), preparo.pedido().vencimento());
				// A cobrança pode ter sido cancelada ou paga enquanto o boleto era emitido.
				if (cobrancas.findById(b.getCobrancaId()).map(c -> c.getStatus() != StatusCobranca.ABERTA).orElse(true)) {
					b.pedirCancelamento();
				}
			});
		}
		catch (FalhaNoGateway e) {
			gravar(boletoId, b -> b.falhou(e.getMessage()));
		}
		catch (RuntimeException e) {
			log.warn("Erro inesperado ao emitir o boleto {}", boletoId, e);
			gravar(boletoId, b -> b.falhou("Não foi possível falar com o gateway. Tente de novo em alguns minutos."));
		}
	}

	private void cancelar(UUID boletoId, ContaDoGateway.Acesso acesso) {
		String idNoGateway = transacao.execute(s -> boletos.findById(boletoId).map(Boleto::getIdNoGateway).orElse(null));
		try {
			acesso.gateway().cancelar(acesso.credencial(), idNoGateway);
			gravar(boletoId, Boleto::cancelado);
		}
		catch (RuntimeException e) {
			// Fica A_CANCELAR e é tentado de novo na próxima rodada.
			log.warn("Não foi possível cancelar o boleto {} no gateway", boletoId, e);
		}
	}

	/** O pedido para o gateway, ou o motivo de não dar para emitir. Nulo se o boleto não está mais pendente. */
	private Preparo preparar(UUID boletoId) {
		Boleto boleto = boletos.findById(boletoId).filter(b -> b.getSituacao() == SituacaoDoBoleto.PENDENTE).orElse(null);
		if (boleto == null) {
			return null;
		}
		Cobranca cobranca = cobrancas.findById(boleto.getCobrancaId()).orElseThrow();
		if (cobranca.getStatus() != StatusCobranca.ABERTA) {
			boleto.pedirCancelamento();
			return null;
		}
		if (cobranca.getResponsavelPessoaId() == null) {
			return Preparo.comProblema("A unidade não tem responsável pela cobrança. Ligue um proprietário ou inquilino à unidade.");
		}
		var dados = cadastro.pagador(cobranca.getResponsavelPessoaId()).orElse(null);
		if (dados == null) {
			return Preparo.comProblema("O responsável pela cobrança não está mais cadastrado.");
		}
		if (dados.documento() == null || dados.documento().isBlank()) {
			return Preparo.comProblema("Falta o CPF ou CNPJ de " + dados.nome() + ", que o banco exige no boleto. Cadastre em Moradores.");
		}
		var condicoes = cobranca.condicoesDoBoletoEm(LocalDate.now(relogio));
		return new Preparo(new PedidoDeBoleto(cobranca.getId(),
				new Pagador(dados.pessoaId(), dados.nome(), dados.documento(), dados.email()),
				condicoes.valor(), condicoes.vencimento(), cobranca.getDescricao(), condicoes.multaPercentual(),
				condicoes.jurosMensalPercentual()), null);
	}

	private void gravar(UUID boletoId, Consumer<Boleto> mudanca) {
		transacao.executeWithoutResult(s -> boletos.findById(boletoId).ifPresent(mudanca));
	}

	private record Preparo(PedidoDeBoleto pedido, String problema) {

		static Preparo comProblema(String problema) {
			return new Preparo(null, problema);
		}

	}

}
