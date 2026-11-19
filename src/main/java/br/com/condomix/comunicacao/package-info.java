/**
 * Comunicação: as notificações do condomínio no app (o sino), por e-mail e por push.
 *
 * <ul>
 *   <li>Os outros módulos publicam o que aconteceu (cobrança disponível, reserva feita, fatura atrasada...). Os
 *       ouvintes ({@code ouvintes}) transformam em notificações para as pessoas certas ({@code DestinatariosDeNotificacoes}).</li>
 *   <li>Os eventos ficam guardados no banco pelo Spring Modulith: se algo falhar, são reprocessados.</li>
 *   <li>Cada acontecimento tem uma chave: a mesma pessoa não é notificada duas vezes.</li>
 *   <li>E-mail e push são canais ({@code CanalDeNotificacao}); a entrega tenta de novo quando falha. O WhatsApp
 *       entra como mais um canal.</li>
 * </ul>
 *
 * <p>Implementa {@code EnvioDeEmail} (compartilhado). Subpacotes (internos): {@code dominio}, {@code persistencia},
 * {@code aplicacao}, {@code canais}, {@code email}, {@code push}, {@code ouvintes} e {@code web}.
 */
package br.com.condomix.comunicacao;
