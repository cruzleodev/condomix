import { ACOMPANHAMENTO, GESTAO } from '../sessao/papeis'

/**
 * Os itens de navegação do app. Cada um diz quem o vê; a mesma lista monta o menu lateral (computador)
 * e a barra de baixo (celular), que mostra só os marcados como {@code noCelular}.
 */
export interface ItemDeNavegacao {
  para: string
  rotulo: string
  icone: string
  /** Papéis que veem o item; vazio = todos. */
  papeis?: string[]
  /** Aparece na barra de baixo do celular (no máximo 5). */
  noCelular?: boolean
  secao?: 'principal' | 'gestao'
  /** Continua no menu com o condomínio suspenso por falta de pagamento (só dá para pagar a assinatura). */
  comPagamentoPendente?: boolean
}

export const ITENS: ItemDeNavegacao[] = [
  { para: '/', rotulo: 'Início', icone: '⌂', noCelular: true },
  { para: '/debitos', rotulo: 'Meus débitos', icone: 'R$', noCelular: true },
  { para: '/reservas', rotulo: 'Reservas', icone: '◷', noCelular: true },
  { para: '/documentos', rotulo: 'Documentos', icone: '▤', noCelular: true },
  { para: '/balancete', rotulo: 'Balancete', icone: '◔' },
  { para: '/gestao', rotulo: 'Gestão', icone: '⚙', papeis: ACOMPANHAMENTO, noCelular: true },
  { para: '/gestao/unidades', rotulo: 'Unidades', icone: '▦', papeis: ACOMPANHAMENTO, secao: 'gestao' },
  { para: '/gestao/moradores', rotulo: 'Moradores', icone: '☺', papeis: ACOMPANHAMENTO, secao: 'gestao' },
  { para: '/gestao/mandatos', rotulo: 'Cargos e mandatos', icone: '★', papeis: ACOMPANHAMENTO, secao: 'gestao' },
  { para: '/gestao/taxa', rotulo: 'Taxa do mês', icone: '%', papeis: ACOMPANHAMENTO, secao: 'gestao' },
  { para: '/gestao/cobrancas', rotulo: 'Cobranças', icone: '$', papeis: ACOMPANHAMENTO, secao: 'gestao' },
  { para: '/gestao/caixa', rotulo: 'Caixa', icone: '⊟', papeis: ACOMPANHAMENTO, secao: 'gestao' },
  { para: '/gestao/espacos', rotulo: 'Espaços e reservas', icone: '◫', papeis: ACOMPANHAMENTO, secao: 'gestao' },
  { para: '/gestao/assinatura', rotulo: 'Assinatura', icone: '✦', papeis: GESTAO, secao: 'gestao', comPagamentoPendente: true },
  { para: '/conta', rotulo: 'Conta', icone: '●', noCelular: true, comPagamentoPendente: true },
]

/** Com {@code pagamentoPendente} (condomínio suspenso por falta de pagamento), só a assinatura e a conta. */
export function itensVisiveis(perfis: string[], pagamentoPendente = false): ItemDeNavegacao[] {
  return ITENS.filter((i) => (!i.papeis || i.papeis.some((p) => perfis.includes(p))) && (!pagamentoPendente || i.comPagamentoPendente))
}

/**
 * Barra de baixo do celular (5 itens): quem tem a aba "Gestão" acha os Documentos pelo Início; o Balancete também
 * fica no Início (cartão "Caixa do condomínio").
 */
export function itensDoCelular(perfis: string[], pagamentoPendente = false): ItemDeNavegacao[] {
  if (pagamentoPendente) return itensVisiveis(perfis, true)
  const visiveis = itensVisiveis(perfis)
  const temGestao = visiveis.some((i) => i.para === '/gestao')
  return visiveis.filter((i) => i.noCelular && !(temGestao && i.para === '/documentos'))
    .sort((a, b) => ITENS.indexOf(a) - ITENS.indexOf(b))
}
