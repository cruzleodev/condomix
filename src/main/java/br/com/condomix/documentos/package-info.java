/**
 * Documentos: convenção, regimento interno, leis e atas de assembleia.
 *
 * <p>API pública: {@link br.com.condomix.documentos.Categoria} e o evento
 * {@link br.com.condomix.documentos.DocumentoPublicado}. O resto é interno:
 * <ul>
 *   <li>{@code dominio}: a entidade e as regras do documento;</li>
 *   <li>{@code aplicacao}: os casos de uso (publicar, consultar, manter);</li>
 *   <li>{@code persistencia}: banco e busca;</li>
 *   <li>{@code armazenamento}: onde os arquivos ficam;</li>
 *   <li>{@code web}: a API HTTP.</li>
 * </ul>
 */
package br.com.condomix.documentos;
