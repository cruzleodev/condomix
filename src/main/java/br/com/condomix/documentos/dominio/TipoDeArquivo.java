package br.com.condomix.documentos.dominio;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Tipos de arquivo aceitos. O tipo é confirmado pelos primeiros bytes do conteúdo (a "assinatura"
 * do formato), e não só pela extensão ou pelo que o navegador informou: um ".pdf" que não é PDF é recusado.
 */
public enum TipoDeArquivo {

	PDF("application/pdf", "25504446", "pdf"),
	JPEG("image/jpeg", "ffd8ff", "jpg", "jpeg"),
	PNG("image/png", "89504e470d0a1a0a", "png"),
	DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "504b0304", "docx"),
	XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "504b0304", "xlsx");

	/** Quantos bytes do início do arquivo bastam para reconhecer qualquer tipo da lista. */
	public static final int BYTES_PARA_IDENTIFICAR = 8;

	private final String tipoConteudo;

	private final byte[] assinatura;

	private final Set<String> extensoes;

	TipoDeArquivo(String tipoConteudo, String assinaturaHex, String... extensoes) {
		this.tipoConteudo = tipoConteudo;
		this.assinatura = HexFormat.of().parseHex(assinaturaHex);
		this.extensoes = Set.of(extensoes);
	}

	public String tipoConteudo() {
		return tipoConteudo;
	}

	/** O tipo cuja extensão bate com o nome do arquivo e cuja assinatura bate com o início do conteúdo. */
	public static Optional<TipoDeArquivo> identificar(String nomeArquivo, byte[] inicioDoConteudo) {
		String extensao = extensao(nomeArquivo);
		return Arrays.stream(values())
				.filter(tipo -> tipo.extensoes.contains(extensao))
				.filter(tipo -> comecaCom(inicioDoConteudo, tipo.assinatura))
				.findFirst();
	}

	public static String extensoesAceitas() {
		return String.join(", ", Arrays.stream(values()).flatMap(t -> t.extensoes.stream()).sorted().toList());
	}

	private static String extensao(String nome) {
		int ponto = nome == null ? -1 : nome.lastIndexOf('.');
		return ponto < 0 ? "" : nome.substring(ponto + 1).toLowerCase(Locale.ROOT);
	}

	private static boolean comecaCom(byte[] conteudo, byte[] prefixo) {
		return conteudo.length >= prefixo.length
				&& Arrays.equals(conteudo, 0, prefixo.length, prefixo, 0, prefixo.length);
	}

}
