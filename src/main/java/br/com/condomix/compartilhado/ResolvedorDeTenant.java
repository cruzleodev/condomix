package br.com.condomix.compartilhado;

import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Informa ao Hibernate qual é o tenant atual (ver {@link TenantAtual}).
 *
 * <ul>
 *   <li>Com condomínio definido: o Hibernate filtra as consultas por ele e grava o tenant_id dele.</li>
 *   <li>Em modo sistema: devolve o id "raiz", que o Hibernate entende como "sem filtro".</li>
 *   <li>Sem nada definido: devolve um id que não existe. Consultas voltam vazias e gravações
 *       falham (chave estrangeira), em vez de vazar ou misturar dados.</li>
 * </ul>
 */
@Component
class ResolvedorDeTenant implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

	static final UUID RAIZ = new UUID(0, 0);

	static final UUID NENHUM = new UUID(0, 1);

	@Override
	public UUID resolveCurrentTenantIdentifier() {
		if (TenantAtual.isSistema()) {
			return RAIZ;
		}
		return TenantAtual.id().orElse(NENHUM);
	}

	@Override
	public boolean isRoot(UUID tenantId) {
		return RAIZ.equals(tenantId);
	}

	@Override
	public boolean validateExistingCurrentSessions() {
		return false;
	}

	@Override
	public void customize(Map<String, Object> hibernateProperties) {
		hibernateProperties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
	}

}
