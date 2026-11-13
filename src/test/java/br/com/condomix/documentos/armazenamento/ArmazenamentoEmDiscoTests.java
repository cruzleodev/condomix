package br.com.condomix.documentos.armazenamento;

import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class ArmazenamentoEmDiscoTests extends ContratoArmazenamentoDeArquivos {

	@TempDir
	Path pasta;

	@Override
	protected ArmazenamentoDeArquivos criar() {
		return new ArmazenamentoEmDisco(pasta);
	}

}
