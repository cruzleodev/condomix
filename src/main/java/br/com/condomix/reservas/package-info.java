/**
 * Reservas dos espaços comuns (salão de festas, churrasqueira, quadra...): os horários de cada espaço, a agenda,
 * as regras e o aluguel.
 *
 * <ul>
 *   <li>A reserva vale na hora, se passar em todas as regras ({@code RegraDeReserva}: antecedência, limite por mês,
 *       bloqueio de inadimplente) e o horário estiver livre.</li>
 *   <li>Com aluguel, a reserva vira cobrança avulsa na unidade (com boleto e Pix, se ligados), pelo financeiro.</li>
 *   <li>Cancelar no prazo do espaço cancela o aluguel; depois dele, o aluguel continua devido.</li>
 * </ul>
 *
 * <p>Usa {@code CadastroParaReservas} (acesso) e {@code CobrancasParaReservas} (financeiro). Não expõe nada a outros
 * módulos. Subpacotes (internos): {@code dominio}, {@code persistencia}, {@code regras}, {@code aplicacao} e {@code web}.
 */
package br.com.condomix.reservas;
