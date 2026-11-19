import type { Espaco } from '../api/tipos'

/** As regras do espaço, em frases curtas para o morador. */
export function resumoDasRegras(e: Espaco): string[] {
  const r = e.regras
  if (!r) return []
  const frases: string[] = []
  const minima = r.antecedenciaMinimaDias ?? 0
  frases.push(minima > 0
    ? `Reserve com pelo menos ${minima} dia(s) de antecedência, até ${r.antecedenciaMaximaDias} dias à frente.`
    : `Reserve até ${r.antecedenciaMaximaDias} dias à frente.`)
  if (r.limitePorMes) frases.push(`Até ${r.limitePorMes} reserva(s) por mês para cada unidade.`)
  if (r.bloqueiaInadimplente) frases.push('Unidade com cobrança vencida não reserva.')
  const dias = r.diasParaCancelar ?? 0
  if ((e.valorAluguel ?? 0) > 0) {
    frases.push(dias > 0
      ? `Cancelando até ${dias} dia(s) antes, o aluguel é cancelado; depois disso, continua devido.`
      : 'Cancelando até o dia da reserva, o aluguel é cancelado.')
  }
  return frases
}

/** O que aconteceu com o aluguel ao cancelar, para mostrar ao morador. */
export const MENSAGEM_DO_CANCELAMENTO = {
  SEM_ALUGUEL: 'Reserva cancelada.',
  CANCELADO: 'Reserva cancelada. A cobrança do aluguel também foi cancelada.',
  MANTIDO: 'Reserva cancelada. Como passou do prazo, o aluguel continua em Meus débitos.',
  JA_PAGO: 'Reserva cancelada. O aluguel já tinha sido pago: combine a devolução com o síndico.',
} as const
