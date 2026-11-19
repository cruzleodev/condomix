import type { BoletoDaCobranca } from '../api/tipos'

/**
 * Enquanto algum boleto está sendo emitido ou cancelado (o servidor faz isso em segundos, em segundo plano),
 * a lista se atualiza sozinha a cada 5 segundos.
 */
export function atualizarEnquantoProcessa(cobrancas: { boleto?: BoletoDaCobranca | null }[] | undefined): number | false {
  return (cobrancas ?? []).some((c) => c.boleto?.situacao === 'PENDENTE' || c.boleto?.situacao === 'A_CANCELAR') ? 5000 : false
}
