package br.com.condomix.acesso;

import java.util.List;
import java.util.UUID;

/** A pessoa excluiu a própria conta. Outros módulos apagam o que guardavam do login (ex.: notificações, push). */
public record ContaExcluida(UUID usuarioId, List<UUID> condominios) {
}
