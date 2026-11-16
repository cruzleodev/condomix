/**
 * Administração da plataforma: o painel do dono do Condomix. Entrada de clientes novos, condomínios,
 * planos, assinaturas e o modo suporte. Todas as rotas ficam em {@code /api/plataforma/**} e exigem o
 * papel ADMIN_PLATAFORMA.
 *
 * <p>Fica acima dos outros módulos e usa só as APIs públicas deles ({@code plataforma} e {@code acesso}).
 * Assim nenhum dos dois precisa depender do outro em mão dupla.
 */
package br.com.condomix.administracao;
