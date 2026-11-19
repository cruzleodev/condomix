/**
 * Faturamento: o Condomix cobra a assinatura de cada condomínio por boleto e Pix, na conta do próprio Condomix
 * no gateway. (O dinheiro que os moradores pagam ao condomínio fica no módulo financeiro.)
 *
 * <ul>
 *   <li>Cliente novo tem {@code 30 dias} de teste grátis; ao fim, a assinatura vira ativa e começa a ser faturada.</li>
 *   <li>Cada fatura cobra um período do plano (mês, semestre ou ano), sai 10 dias antes e vence no início dele.</li>
 *   <li>Depois de 15 dias de atraso, o acesso do condomínio é suspenso; volta sozinho quando a fatura é paga.</li>
 *   <li>Trocar de plano vale a partir da próxima fatura.</li>
 * </ul>
 *
 * <p>API pública: {@code ContaDoCondomixNoGateway} e {@code FaturasDosClientes} (painel da plataforma) e
 * {@code SituacaoDaFatura}. A tela do síndico é servida aqui mesmo ({@code web}).
 *
 * <p>Subpacotes (internos): {@code dominio}, {@code persistencia}, {@code aplicacao}, {@code web}, {@code asaas}
 * (aviso de pagamento) e {@code simulado} (botão "simular pagamento").
 */
package br.com.condomix.faturamento;
