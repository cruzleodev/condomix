package br.com.condomix.plataforma;

/** Por que o condomínio está suspenso. */
public enum MotivoDaSuspensao {

	/** O administrador da plataforma suspendeu. Só ele reativa. */
	MANUAL,

	/** A fatura da assinatura passou da tolerância sem pagamento. Volta sozinho quando a fatura é paga. */
	FALTA_DE_PAGAMENTO

}
