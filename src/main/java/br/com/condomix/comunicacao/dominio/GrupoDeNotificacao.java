package br.com.condomix.comunicacao.dominio;

/** Os assuntos das notificações. Cada pessoa liga ou desliga o e-mail e o push de cada grupo. */
public enum GrupoDeNotificacao {

	COBRANCAS("Cobranças", "boleto disponível, vencimento chegando, pagamento confirmado"),
	RESERVAS("Reservas", "reserva confirmada, lembrete na véspera, cancelamento"),
	ASSINATURA("Assinatura do Condomix", "fatura disponível ou atrasada, acesso suspenso, fim do teste (só para a gestão)"),
	DOCUMENTOS("Documentos", "documento novo publicado");

	private final String rotulo;

	private final String exemplos;

	GrupoDeNotificacao(String rotulo, String exemplos) {
		this.rotulo = rotulo;
		this.exemplos = exemplos;
	}

	public String rotulo() {
		return rotulo;
	}

	public String exemplos() {
		return exemplos;
	}

}
