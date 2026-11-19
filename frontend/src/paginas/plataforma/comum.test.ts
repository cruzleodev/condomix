import { describe, expect, it } from 'vitest'
import { ROTULOS } from '../../util/formato'
import { descricaoDaAssinatura } from './comum'

describe('histórico da assinatura', () => {
  it('descreve a assinatura só com o que o evento tem', () => {
    expect(descricaoDaAssinatura({ nomeDoPlano: 'Completo', periodicidade: 'MENSAL', status: 'TESTE' })).toBe('Completo · Mensal · Em teste')
    expect(descricaoDaAssinatura({ nomeDoPlano: 'Essencial', periodicidade: 'ANUAL' })).toBe('Essencial · Anual')
    expect(descricaoDaAssinatura({ tipo: 'SUSPENSAO' })).toBe('—')
  })

  it('tem rótulo para cada tipo de mudança', () => {
    expect(ROTULOS.tipoDeEventoDaAssinatura.TROCA_DE_PLANO).toBe('Trocou o plano')
    expect(ROTULOS.tipoDeEventoDaAssinatura.REATIVACAO).toBe('Acesso reativado')
  })
})
