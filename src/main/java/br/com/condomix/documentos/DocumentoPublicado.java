package br.com.condomix.documentos;

import java.util.UUID;

/**
 * Evento publicado quando um documento novo é disponibilizado aos moradores.
 * Outros módulos podem reagir a ele sem depender do módulo documentos (ex.: comunicação avisa pelo WhatsApp).
 */
public record DocumentoPublicado(UUID tenantId, UUID documentoId, String titulo, Categoria categoria) {
}
