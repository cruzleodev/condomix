package br.com.condomix.documentos.aplicacao;

import br.com.condomix.compartilhado.TenantAtual;
import br.com.condomix.documentos.Categoria;
import br.com.condomix.documentos.armazenamento.ArmazenamentoDeArquivos;
import br.com.condomix.documentos.dominio.ArquivoDoDocumento;
import br.com.condomix.documentos.dominio.Documento;
import br.com.condomix.documentos.persistencia.DocumentoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.UUID;

/** Caso de uso: qualquer pessoa do condomínio lista, busca, vê e baixa documentos. */
@Service
@Transactional(readOnly = true)
public class ConsultaDeDocumentos {

	static final int TAMANHO_MAXIMO_DA_PAGINA = 100;

	private static final Sort MAIS_RECENTES = Sort.by(
			Sort.Order.desc("dataReferencia").nullsLast(), Sort.Order.desc("enviadoEm"));

	private final DocumentoRepository documentos;

	private final ArmazenamentoDeArquivos armazenamento;

	public ConsultaDeDocumentos(DocumentoRepository documentos, ArmazenamentoDeArquivos armazenamento) {
		this.documentos = documentos;
		this.armazenamento = armazenamento;
	}

	/** O conteúdo de um arquivo pronto para download. Quem recebe deve fechar o {@code conteudo}. */
	public record ConteudoDoArquivo(ArquivoDoDocumento arquivo, InputStream conteudo) {
	}

	/**
	 * Com {@code termo}: busca em português, ordenada por relevância.
	 * Sem {@code termo}: os mais recentes primeiro (pela data de referência, depois pelo envio).
	 */
	public Page<Documento> listar(Categoria categoria, String termo, int pagina, int tamanho) {
		var pedido = PageRequest.of(Math.max(pagina, 0), Math.clamp(tamanho, 1, TAMANHO_MAXIMO_DA_PAGINA));
		if (termo != null && !termo.isBlank()) {
			return documentos.buscar(TenantAtual.obrigatorio(), categoria == null ? null : categoria.name(),
					termo.strip(), pedido);
		}
		var ordenado = pedido.withSort(MAIS_RECENTES);
		return categoria == null ? documentos.findAll(ordenado) : documentos.findByCategoria(categoria, ordenado);
	}

	public Documento obter(UUID id) {
		return documentos.findById(id).orElseThrow(DocumentoNaoEncontradoException::new);
	}

	public ConteudoDoArquivo abrirArquivo(UUID id) {
		ArquivoDoDocumento arquivo = obter(id).getArquivo();
		return new ConteudoDoArquivo(arquivo, armazenamento.abrir(arquivo.chave()));
	}

}
