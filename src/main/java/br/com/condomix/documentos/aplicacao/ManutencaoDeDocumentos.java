package br.com.condomix.documentos.aplicacao;

import br.com.condomix.documentos.dominio.DadosDoDocumento;
import br.com.condomix.documentos.dominio.Documento;
import br.com.condomix.documentos.persistencia.DocumentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Caso de uso: o síndico corrige os dados de um documento ou o remove da lista. */
@Service
@Transactional
public class ManutencaoDeDocumentos {

	private final DocumentoRepository documentos;

	private final Clock relogio;

	public ManutencaoDeDocumentos(DocumentoRepository documentos, Clock relogio) {
		this.documentos = documentos;
		this.relogio = relogio;
	}

	public Documento alterar(UUID id, DadosDoDocumento dados) {
		Documento documento = documentos.findById(id).orElseThrow(DocumentoNaoEncontradoException::new);
		documento.alterar(dados);
		return documento;
	}

	/** Some da lista para todos, mas o registro e o arquivo continuam guardados (valor legal). */
	public void remover(UUID id, UUID usuarioId) {
		Documento documento = documentos.findById(id).orElseThrow(DocumentoNaoEncontradoException::new);
		documento.remover(usuarioId, Instant.now(relogio));
	}

}
