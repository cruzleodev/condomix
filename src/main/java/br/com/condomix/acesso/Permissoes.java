package br.com.condomix.acesso;

/** Expressões de permissão usadas nos {@code @PreAuthorize}, num só lugar. */
public final class Permissoes {

	/** Quem administra o condomínio: cadastra, convida, registra mandatos. */
	public static final String GESTAO = "hasAnyRole('SINDICO', 'ADMINISTRADORA')";

	/**
	 * Quem pode consultar o cadastro e o financeiro: gestão, subsíndico, conselho e o suporte do Condomix.
	 * O suporte só lê (qualquer escrita é recusada pela segurança) e cada acesso fica auditado.
	 */
	public static final String CONSULTA_DO_CADASTRO =
			"hasAnyRole('SINDICO', 'SUBSINDICO', 'ADMINISTRADORA', 'CONSELHEIRO', 'SUPORTE')";

	private Permissoes() {
	}

}
