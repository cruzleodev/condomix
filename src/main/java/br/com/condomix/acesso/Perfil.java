package br.com.condomix.acesso;

/**
 * Os papéis de um usuário dentro de um condomínio. Viram {@code ROLE_<nome>} no Spring Security.
 *
 * <p>Há dois tipos:
 * <ul>
 *   <li><b>Cargos eleitos</b> (síndico, subsíndico, conselheiro): vêm de um {@code Mandato} vigente.
 *       Não são gravados em {@link PerfilAcesso}.</li>
 *   <li><b>Papéis sem mandato</b> (morador, administradora, portaria): gravados em {@link PerfilAcesso}.</li>
 * </ul>
 */
public enum Perfil {

	SINDICO(true),
	SUBSINDICO(true),
	CONSELHEIRO(true),
	ADMINISTRADORA(false),
	MORADOR(false),
	PORTARIA(false);

	private final boolean atribuidoPorMandato;

	Perfil(boolean atribuidoPorMandato) {
		this.atribuidoPorMandato = atribuidoPorMandato;
	}

	public boolean atribuidoPorMandato() {
		return atribuidoPorMandato;
	}

}
