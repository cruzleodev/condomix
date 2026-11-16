package br.com.condomix.acesso;

/** Expressões de permissão usadas nos {@code @PreAuthorize}, num só lugar. */
public final class Permissoes {

	/** Quem administra o condomínio: cadastra, convida, registra mandatos. */
	public static final String GESTAO = "hasAnyRole('SINDICO', 'ADMINISTRADORA')";

	/** Quem pode consultar o cadastro (unidades e pessoas): gestão, subsíndico e conselho. */
	public static final String CONSULTA_DO_CADASTRO = "hasAnyRole('SINDICO', 'SUBSINDICO', 'ADMINISTRADORA', 'CONSELHEIRO')";

	private Permissoes() {
	}

}
