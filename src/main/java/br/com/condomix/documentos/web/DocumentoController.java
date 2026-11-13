package br.com.condomix.documentos.web;

import br.com.condomix.compartilhado.Pagina;
import br.com.condomix.documentos.Categoria;
import br.com.condomix.documentos.aplicacao.ArquivoRecebido;
import br.com.condomix.documentos.aplicacao.ConsultaDeDocumentos;
import br.com.condomix.documentos.aplicacao.ManutencaoDeDocumentos;
import br.com.condomix.documentos.aplicacao.PublicacaoDeDocumentos;
import br.com.condomix.documentos.dominio.DadosDoDocumento;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * API de documentos. Só HTTP: valida, converte e delega aos casos de uso (SOLID: S).
 * Ler: qualquer perfil do condomínio. Publicar, editar e remover: síndico ou administradora.
 */
@Tag(name = "Documentos")
@RestController
@RequestMapping("/api/documentos")
class DocumentoController {

	private static final String QUEM_GERENCIA = "hasAnyRole('SINDICO', 'ADMINISTRADORA')";

	private final PublicacaoDeDocumentos publicacao;

	private final ConsultaDeDocumentos consulta;

	private final ManutencaoDeDocumentos manutencao;

	DocumentoController(PublicacaoDeDocumentos publicacao, ConsultaDeDocumentos consulta,
			ManutencaoDeDocumentos manutencao) {
		this.publicacao = publicacao;
		this.consulta = consulta;
		this.manutencao = manutencao;
	}

	/** Publicação: os campos do documento mais o arquivo, num formulário multipart. */
	record FormularioPublicacao(
			@NotBlank @Size(max = 200) String titulo,
			@NotNull Categoria categoria,
			@Size(max = 2000) String descricao,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataReferencia,
			@NotNull MultipartFile arquivo) {

		DadosDoDocumento paraDados() {
			return new DadosDoDocumento(titulo, categoria, descricao, dataReferencia);
		}

	}

	/** Edição: só os campos, em JSON. O arquivo não muda; para trocar, remova e publique de novo. */
	record FormularioEdicao(
			@NotBlank @Size(max = 200) String titulo,
			@NotNull Categoria categoria,
			@Size(max = 2000) String descricao,
			LocalDate dataReferencia) {

		DadosDoDocumento paraDados() {
			return new DadosDoDocumento(titulo, categoria, descricao, dataReferencia);
		}

	}

	record CategoriaResposta(Categoria codigo, String rotulo) {
	}

	@Operation(summary = "Categorias de documento, com o nome para exibir")
	@GetMapping("/categorias")
	List<CategoriaResposta> categorias() {
		return Arrays.stream(Categoria.values()).map(c -> new CategoriaResposta(c, c.rotulo())).toList();
	}

	@Operation(summary = "Listar ou buscar documentos",
			description = "Com 'busca', procura no título e na descrição (sem diferenciar acentos) e ordena por relevância. "
					+ "Sem 'busca', lista os mais recentes primeiro.")
	@GetMapping
	Pagina<DocumentoResposta> listar(
			@RequestParam(required = false) Categoria categoria,
			@Parameter(description = "Ex.: convencao, \"assembleia geral\", obras -piscina")
			@RequestParam(required = false) String busca,
			@RequestParam(defaultValue = "0") int pagina,
			@RequestParam(defaultValue = "20") int tamanho) {
		return Pagina.de(consulta.listar(categoria, busca, pagina, tamanho), DocumentoResposta::de);
	}

	@Operation(summary = "Dados de um documento")
	@GetMapping("/{id}")
	DocumentoResposta obter(@PathVariable UUID id) {
		return DocumentoResposta.de(consulta.obter(id));
	}

	@Operation(summary = "Baixar ou abrir o arquivo do documento")
	@GetMapping("/{id}/arquivo")
	ResponseEntity<InputStreamResource> baixar(@PathVariable UUID id) {
		var conteudo = consulta.abrirArquivo(id);
		var arquivo = conteudo.arquivo();
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(arquivo.tipoConteudo()))
				.contentLength(arquivo.tamanho())
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.inline().filename(arquivo.nome(), StandardCharsets.UTF_8).build().toString())
				.body(new InputStreamResource(conteudo.conteudo()));
	}

	@Operation(summary = "Publicar documento (síndico ou administradora)",
			description = "Arquivos aceitos: PDF, JPG, PNG, DOCX e XLSX, até 20 MB.")
	@PreAuthorize(QUEM_GERENCIA)
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<DocumentoResposta> publicar(
			@Valid @ModelAttribute FormularioPublicacao formulario,
			@AuthenticationPrincipal Jwt jwt) {
		MultipartFile arquivo = formulario.arquivo();
		var recebido = new ArquivoRecebido(arquivo.getOriginalFilename(), arquivo::getInputStream);
		var documento = publicacao.publicar(formulario.paraDados(), recebido, UUID.fromString(jwt.getSubject()));
		var endereco = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(documento.getId());
		return ResponseEntity.created(endereco).body(DocumentoResposta.de(documento));
	}

	@Operation(summary = "Corrigir os dados de um documento (síndico ou administradora)")
	@PreAuthorize(QUEM_GERENCIA)
	@PutMapping("/{id}")
	DocumentoResposta alterar(@PathVariable UUID id, @Valid @RequestBody FormularioEdicao formulario) {
		return DocumentoResposta.de(manutencao.alterar(id, formulario.paraDados()));
	}

	@Operation(summary = "Remover documento da lista (síndico ou administradora)",
			description = "O documento deixa de aparecer, mas continua guardado.")
	@PreAuthorize(QUEM_GERENCIA)
	@DeleteMapping("/{id}")
	ResponseEntity<Void> remover(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
		manutencao.remover(id, UUID.fromString(jwt.getSubject()));
		return ResponseEntity.noContent().build();
	}

}
