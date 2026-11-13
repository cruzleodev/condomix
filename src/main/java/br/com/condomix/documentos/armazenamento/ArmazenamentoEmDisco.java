package br.com.condomix.documentos.armazenamento;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Guarda os arquivos numa pasta do disco ({@code condomix.armazenamento.diretorio}).
 * Usado em desenvolvimento; em produção será trocado por um armazenamento em nuvem.
 */
@Component
public class ArmazenamentoEmDisco implements ArmazenamentoDeArquivos {

	private final Path raiz;

	public ArmazenamentoEmDisco(@Value("${condomix.armazenamento.diretorio}") Path raiz) {
		this.raiz = raiz.toAbsolutePath().normalize();
	}

	@Override
	public long guardar(String chave, InputStream conteudo) {
		Path destino = caminho(chave);
		if (Files.exists(destino)) {
			throw new IllegalStateException("Já existe um arquivo com a chave " + chave);
		}
		try {
			Files.createDirectories(destino.getParent());
			// Grava num arquivo temporário e só depois move: nunca fica um arquivo pela metade com o nome final.
			Path temporario = Files.createTempFile(destino.getParent(), ".envio-", ".tmp");
			try {
				long bytes = Files.copy(conteudo, temporario, StandardCopyOption.REPLACE_EXISTING);
				Files.move(temporario, destino, StandardCopyOption.ATOMIC_MOVE);
				return bytes;
			}
			finally {
				Files.deleteIfExists(temporario);
			}
		}
		catch (FileAlreadyExistsException e) {
			throw new IllegalStateException("Já existe um arquivo com a chave " + chave, e);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public InputStream abrir(String chave) {
		try {
			return Files.newInputStream(caminho(chave));
		}
		catch (NoSuchFileException e) {
			throw new ArquivoInexistenteException(chave);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public boolean existe(String chave) {
		return Files.isRegularFile(caminho(chave));
	}

	@Override
	public void remover(String chave) {
		try {
			Files.deleteIfExists(caminho(chave));
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Resolve a chave dentro da pasta raiz, recusando chaves que tentem sair dela (ex.: "../../etc"). */
	private Path caminho(String chave) {
		if (chave == null || chave.isBlank()) {
			throw new IllegalArgumentException("Chave vazia");
		}
		Path caminho = raiz.resolve(chave).normalize();
		if (!caminho.startsWith(raiz) || caminho.equals(raiz)) {
			throw new IllegalArgumentException("Chave inválida: " + chave);
		}
		return caminho;
	}

}
