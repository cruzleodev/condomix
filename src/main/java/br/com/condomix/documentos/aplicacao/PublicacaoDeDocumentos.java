package br.com.condomix.documentos.aplicacao;

import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.documentos.DocumentoPublicado;
import br.com.condomix.documentos.armazenamento.ArmazenamentoDeArquivos;
import br.com.condomix.documentos.dominio.ArquivoDoDocumento;
import br.com.condomix.documentos.dominio.DadosDoDocumento;
import br.com.condomix.documentos.dominio.Documento;
import br.com.condomix.documentos.dominio.TipoDeArquivo;
import br.com.condomix.documentos.persistencia.DocumentoRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Caso de uso: o síndico publica um documento novo no condomínio atual. */
@Service
public class PublicacaoDeDocumentos {

	private final DocumentoRepository documentos;

	private final ArmazenamentoDeArquivos armazenamento;

	private final ApplicationEventPublisher eventos;

	public PublicacaoDeDocumentos(DocumentoRepository documentos, ArmazenamentoDeArquivos armazenamento,
			ApplicationEventPublisher eventos) {
		this.documentos = documentos;
		this.armazenamento = armazenamento;
		this.eventos = eventos;
	}

	@Transactional
	public Documento publicar(DadosDoDocumento dados, ArquivoRecebido arquivo, UUID usuarioId) {
		UUID tenantId = TenantAtual.obrigatorio();
		String nome = nomeSemPasta(arquivo.nome());
		TipoDeArquivo tipo = identificar(nome, arquivo);

		// A chave começa pelo condomínio: os arquivos de cada cliente ficam separados também no armazenamento.
		String chave = tenantId + "/" + UUID.randomUUID();
		MessageDigest sha256 = sha256();
		long tamanho;
		try (InputStream conteudo = new DigestInputStream(arquivo.conteudo().abrir(), sha256)) {
			tamanho = armazenamento.guardar(chave, conteudo);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		try {
			var dadosDoArquivo = new ArquivoDoDocumento(nome, tipo.tipoConteudo(), tamanho,
					HexFormat.of().formatHex(sha256.digest()), chave);
			Documento documento = documentos.saveAndFlush(new Documento(tenantId, dados, dadosDoArquivo, usuarioId));
			eventos.publishEvent(new DocumentoPublicado(tenantId, documento.getId(), documento.getTitulo(),
					documento.getCategoria()));
			return documento;
		}
		catch (RuntimeException e) {
			armazenamento.remover(chave); // não deixa arquivo órfão se o banco recusar
			throw e;
		}
	}

	private static TipoDeArquivo identificar(String nome, ArquivoRecebido arquivo) {
		byte[] inicio;
		try (InputStream conteudo = arquivo.conteudo().abrir()) {
			inicio = conteudo.readNBytes(TipoDeArquivo.BYTES_PARA_IDENTIFICAR);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		if (inicio.length == 0) {
			throw new ArquivoInvalidoException("O arquivo está vazio");
		}
		return TipoDeArquivo.identificar(nome, inicio).orElseThrow(() -> new ArquivoInvalidoException(
				"Tipo de arquivo não aceito ou conteúdo não corresponde à extensão. Aceitos: "
						+ TipoDeArquivo.extensoesAceitas()));
	}

	/** Remove pastas do nome (alguns navegadores mandam "C:\\pasta\\ata.pdf") e limita o tamanho. */
	private static String nomeSemPasta(String nome) {
		if (nome == null || nome.isBlank()) {
			throw new ArquivoInvalidoException("O arquivo precisa ter um nome");
		}
		String semPasta = nome.substring(Math.max(nome.lastIndexOf('/'), nome.lastIndexOf('\\')) + 1).strip();
		return semPasta.length() > 255 ? semPasta.substring(semPasta.length() - 255) : semPasta;
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
