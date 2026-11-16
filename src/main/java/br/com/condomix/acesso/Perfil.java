package br.com.condomix.acesso;

/**
 * Os papéis de um usuário. Viram {@code ROLE_<nome>} no Spring Security. Cada papel tem uma origem:
 * <ul>
 *   <li>{@link Origem#MANDATO}: cargos eleitos (síndico, subsíndico, conselheiro), vindos de um {@code Mandato} vigente;</li>
 *   <li>{@link Origem#PERFIL}: papéis sem mandato (morador, administradora, portaria), gravados em {@link PerfilAcesso};</li>
 *   <li>{@link Origem#PLATAFORMA}: papéis do Condomix (administrador e o acesso de suporte), fora dos condomínios.</li>
 * </ul>
 */
public enum Perfil {

	SINDICO(Origem.MANDATO),
	SUBSINDICO(Origem.MANDATO),
	CONSELHEIRO(Origem.MANDATO),
	ADMINISTRADORA(Origem.PERFIL),
	MORADOR(Origem.PERFIL),
	PORTARIA(Origem.PERFIL),
	/** Dono e equipe do Condomix: gerencia condomínios, planos e assinaturas. Não vê dados internos dos condomínios. */
	ADMIN_PLATAFORMA(Origem.PLATAFORMA),
	/** Token temporário, só de leitura, que o administrador abre para dar suporte a um condomínio (auditado). */
	SUPORTE(Origem.PLATAFORMA);

	public enum Origem { MANDATO, PERFIL, PLATAFORMA }

	private final Origem origem;

	Perfil(Origem origem) {
		this.origem = origem;
	}

	public boolean atribuidoPorMandato() {
		return origem == Origem.MANDATO;
	}

	/** Se pode ser gravado em {@link PerfilAcesso}. */
	public boolean gravadoComoPerfil() {
		return origem == Origem.PERFIL;
	}

}
