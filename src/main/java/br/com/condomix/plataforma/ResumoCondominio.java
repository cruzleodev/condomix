package br.com.condomix.plataforma;

import java.util.UUID;

/** O que outros módulos precisam saber de um condomínio. */
public record ResumoCondominio(UUID id, String nome) {
}
