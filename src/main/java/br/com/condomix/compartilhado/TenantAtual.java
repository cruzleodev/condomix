package br.com.condomix.compartilhado;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * O condomínio (tenant) em que o código está rodando.
 *
 * <p>Numa requisição autenticada, o filtro de segurança define o tenant a partir do token JWT.
 * O Hibernate usa esse valor para filtrar automaticamente todas as entidades com {@code @TenantId}:
 * um condomínio nunca enxerga os dados de outro.
 *
 * <p>Usa {@link ScopedValue} (Java 25): o valor vale só dentro do bloco e some ao sair dele.
 *
 * <p><b>Atenção:</b> o Hibernate lê o tenant quando a transação começa. Por isso o tenant tem que ser
 * definido <i>antes</i> de abrir a transação, nunca dentro de um método {@code @Transactional}.
 */
public final class TenantAtual {

	private static final ScopedValue<UUID> TENANT = ScopedValue.newInstance();

	private static final ScopedValue<Boolean> SISTEMA = ScopedValue.newInstance();

	private TenantAtual() {
	}

	/** Tarefa executada dentro de um contexto de tenant. */
	@FunctionalInterface
	public interface Tarefa<T, X extends Throwable> {
		T executar() throws X;
	}

	/** O tenant atual, se houver. */
	public static Optional<UUID> id() {
		return TENANT.isBound() ? Optional.of(TENANT.get()) : Optional.empty();
	}

	/** O tenant atual; falha se o código estiver rodando fora de um condomínio. */
	public static UUID obrigatorio() {
		return id().orElseThrow(() -> new IllegalStateException("Nenhum condomínio selecionado"));
	}

	/** Executa a tarefa dentro do condomínio informado. */
	public static <T, X extends Throwable> T executarComo(UUID tenantId, Tarefa<T, X> tarefa) throws X {
		Objects.requireNonNull(tenantId, "tenantId");
		return ScopedValue.where(TENANT, tenantId).where(SISTEMA, false).call(tarefa::executar);
	}

	/**
	 * Executa a tarefa enxergando os dados de <b>todos</b> os condomínios, só para leitura.
	 * Uso restrito a rotinas do sistema, como o login (que precisa achar os perfis do usuário em
	 * qualquer condomínio). Nunca usar para atender pedidos de um condomínio.
	 */
	public static <T, X extends Throwable> T comoSistema(Tarefa<T, X> tarefa) throws X {
		return ScopedValue.where(SISTEMA, true).call(tarefa::executar);
	}

	static boolean isSistema() {
		return SISTEMA.isBound() && SISTEMA.get();
	}

}
