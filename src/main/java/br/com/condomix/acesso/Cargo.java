package br.com.condomix.acesso;

/** Cargos eleitos em assembleia. Cada um dá ao ocupante o {@link Perfil} correspondente enquanto o mandato vigorar. */
public enum Cargo {

	SINDICO("Síndico", true, Perfil.SINDICO),
	SUBSINDICO("Subsíndico", true, Perfil.SUBSINDICO),
	CONSELHEIRO("Conselheiro", false, Perfil.CONSELHEIRO);

	private final String rotulo;

	private final boolean ocupanteUnico;

	private final Perfil perfil;

	Cargo(String rotulo, boolean ocupanteUnico, Perfil perfil) {
		this.rotulo = rotulo;
		this.ocupanteUnico = ocupanteUnico;
		this.perfil = perfil;
	}

	public String rotulo() {
		return rotulo;
	}

	/** Síndico e subsíndico: um por vez, e a posse do sucessor encerra o mandato anterior. Conselho: vários. */
	public boolean ocupanteUnico() {
		return ocupanteUnico;
	}

	public Perfil perfil() {
		return perfil;
	}

}
