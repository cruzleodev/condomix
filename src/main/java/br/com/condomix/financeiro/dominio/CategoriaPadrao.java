package br.com.condomix.financeiro.dominio;

/**
 * O plano de contas inicial de todo condomínio. As categorias de receita marcadas são usadas
 * automaticamente quando um pagamento de cobrança entra no caixa.
 */
public enum CategoriaPadrao {

	TAXA_CONDOMINIAL("Taxa condominial", Natureza.RECEITA),
	FUNDO_RESERVA("Fundo de reserva", Natureza.RECEITA),
	TAXA_EXTRA("Taxas extras", Natureza.RECEITA),
	MULTAS_E_JUROS("Multas e juros por atraso", Natureza.RECEITA),
	MULTAS_DISCIPLINARES("Multas disciplinares", Natureza.RECEITA),
	ALUGUEL_DE_ESPACOS("Aluguel de espaços", Natureza.RECEITA),
	CONSUMO("Consumo (água, gás)", Natureza.RECEITA),
	RENDIMENTOS("Rendimentos de aplicações", Natureza.RECEITA),
	OUTRAS_RECEITAS("Outras receitas", Natureza.RECEITA),

	PESSOAL("Pessoal e encargos", Natureza.DESPESA),
	AGUA_E_ESGOTO("Água e esgoto", Natureza.DESPESA),
	ENERGIA("Energia elétrica", Natureza.DESPESA),
	GAS("Gás", Natureza.DESPESA),
	MANUTENCAO("Manutenção e conservação", Natureza.DESPESA),
	LIMPEZA("Limpeza", Natureza.DESPESA),
	SEGURANCA_E_PORTARIA("Segurança e portaria", Natureza.DESPESA),
	ADMINISTRACAO("Administração (administradora, síndico)", Natureza.DESPESA),
	SEGUROS("Seguros", Natureza.DESPESA),
	IMPOSTOS_E_TAXAS("Impostos e taxas", Natureza.DESPESA),
	TARIFAS_BANCARIAS("Tarifas bancárias", Natureza.DESPESA),
	OBRAS("Obras e benfeitorias", Natureza.DESPESA),
	OUTRAS_DESPESAS("Outras despesas", Natureza.DESPESA);

	private final String nome;

	private final Natureza natureza;

	CategoriaPadrao(String nome, Natureza natureza) {
		this.nome = nome;
		this.natureza = natureza;
	}

	public String nome() {
		return nome;
	}

	public Natureza natureza() {
		return natureza;
	}

}
