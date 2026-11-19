import { describe, expect, it } from 'vitest'
import { ROTULOS } from '../../util/formato'
import { descricaoDaAssinatura } from './comum'

describe('histórico da assinatura', () => {
  it('descreve a assinatura só com o que o evento tem', () => {
    expect(descricaoDaAssinatura({ nomeDoPlano: 'Completo', periodicidade: 'MENSAL', status: 'TESTE', fimDoTeste: '2026-12-19' }))
      .toBe('Completo · Mensal · Em teste até 19/12/2026')
    expect(descricaoDaAssinatura({ nomeDoPlano: 'Completo', periodicidade: 'MENSAL', status: 'TESTE' })).toBe('Completo · Mensal · Em teste sem prazo')
    expect(descricaoDaAssinatura({ nomeDoPlano: 'Completo', periodicidade: 'MENSAL', status: 'ATIVA' })).toBe('Completo · Mensal · Ativa')
    expect(descricaoDaAssinatura({ nomeDoPlano: 'Essencial', periodicidade: 'ANUAL' })).toBe('Essencial · Anual')
    expect(descricaoDaAssinatura({ tipo: 'SUSPENSAO' })).toBe('—')
  })

  it('tem rótulo para cada tipo de mudança', () => {
    expect(ROTULOS.tipoDeEventoDaAssinatura.TROCA_DE_PLANO).toBe('Trocou o plano')
    expect(ROTULOS.tipoDeEventoDaAssinatura.REATIVACAO).toBe('Acesso reativado')
    expect(ROTULOS.tipoDeEventoDaAssinatura.SUSPENSAO_POR_FALTA_DE_PAGAMENTO).toBe('Suspenso por falta de pagamento')
  })
})
