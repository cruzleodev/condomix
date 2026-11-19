package br.com.condomix.faturamento;

import br.com.condomix.pagamentos.GatewayDePagamento.FormaDoPagamento;
import br.com.condomix.pagamentos.TipoDeGateway;
import br.com.condomix.plataforma.Periodicidade;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** As faturas da assinatura de todos os clientes, para o painel do administrador da plataforma. */
public interface FaturasDosClientes {

	/** Todas, as mais recentes primeiro. */
	List<FaturaDeCliente> todas();

	List<FaturaDeCliente> doCliente(UUID condominioId);

	/** A 2ª via, para mandar ao síndico. */
	PagamentoDaFatura segundaVia(UUID faturaId);

	/** Depois de corrigir o motivo da falha (ex.: o CNPJ do condomínio). */
	void tentarDeNovo(UUID faturaId);

	/** Perdoa a fatura (cancela o boleto no gateway). Se era a que mantinha o acesso suspenso, o acesso volta. */
	void cancelar(UUID faturaId, UUID administradorId);

	/** Roda agora a rotina que normalmente roda sozinha de hora em hora: fim dos testes, faturas novas e suspensões. */
	ResumoDaRotina rodarRotinaAgora();

	/**
	 * @param atrasada    emitida e com o vencimento no passado
	 * @param suspensaoEm quando o acesso é suspenso se não pagar; vazio se não está esperando pagamento
	 */
	record FaturaDeCliente(UUID id, UUID condominioId, String condominio, String plano, Periodicidade periodicidade,
			LocalDate periodoInicio, LocalDate periodoFim, BigDecimal valor, LocalDate vencimento,
			SituacaoDaFatura situacao, boolean atrasada, LocalDate suspensaoEm, String erro, TipoDeGateway gateway,
			LocalDate pagoEm, BigDecimal valorPago, FormaDoPagamento forma, BigDecimal tarifa) {
	}

	/** Boleto e Pix da fatura. {@code pixQrCode}: imagem PNG em Base64. */
	record PagamentoDaFatura(BigDecimal valor, LocalDate vencimento, TipoDeGateway gateway, String url,
			String linhaDigitavel, String pixCopiaECola, String pixQrCode) {
	}

	record ResumoDaRotina(int testesEncerrados, int faturasGeradas, int condominiosSuspensos) {
	}

}
