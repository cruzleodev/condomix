import { ACOMPANHAMENTO } from '../sessao/papeis'

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
}

export const ITENS: ItemDeNavegacao[] = [
  { para: '/', rotulo: 'Início', icone: '⌂', noCelular: true },
  { para: '/debitos', rotulo: 'Meus débitos', icone: 'R$', noCelular: true },
  { para: '/documentos', rotulo: 'Documentos', icone: '▤', noCelular: true },
  { para: '/balancete', rotulo: 'Balancete', icone: '◔' },
  { para: '/gestao', rotulo: 'Gestão', icone: '⚙', papeis: ACOMPANHAMENTO, noCelular: true },
  { para: '/gestao/unidades', rotulo: 'Unidades', icone: '▦', papeis: ACOMPANHAMENTO, secao: 'gestao' },
  { para: '/gestao/moradores', rotulo: 'Moradores', icone: '☺', papeis: ACOMPANHAMENTO, secao: 'gestao' },
  { para: '/gestao/mandatos', rotulo: 'Cargos e mandatos', icone: '★', papeis: ACOMPANHAMENTO, secao: 'gestao' },
  { para: '/gestao/taxa', rotulo: 'Taxa do mês', icone: '%', papeis: ACOMPANHAMENTO, secao: 'gestao' },
  { para: '/gestao/cobrancas', rotulo: 'Cobranças', icone: '$', papeis: ACOMPANHAMENTO, secao: 'gestao' },
  { para: '/gestao/caixa', rotulo: 'Caixa', icone: '⊟', papeis: ACOMPANHAMENTO, secao: 'gestao' },
  { para: '/conta', rotulo: 'Conta', icone: '●', noCelular: true },
]

export function itensVisiveis(perfis: string[]): ItemDeNavegacao[] {
  return ITENS.filter((i) => !i.papeis || i.papeis.some((p) => perfis.includes(p)))
}

/** Barra de baixo do celular: sem o item "Gestão", o Balancete ocupa o lugar. */
export function itensDoCelular(perfis: string[]): ItemDeNavegacao[] {
  const visiveis = itensVisiveis(perfis)
  const temGestao = visiveis.some((i) => i.para === '/gestao')
  return visiveis.filter((i) => i.noCelular || (!temGestao && i.para === '/balancete'))
    .sort((a, b) => ITENS.indexOf(a) - ITENS.indexOf(b))
}
