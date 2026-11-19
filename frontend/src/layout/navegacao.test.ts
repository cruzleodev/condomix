import { describe, expect, it } from 'vitest'
import { itensDoCelular, itensVisiveis } from './navegacao'

const rotas = (itens: { para: string }[]) => itens.map((i) => i.para)

describe('navegação por papel', () => {
  it('morador não vê a gestão; no celular, o balancete ocupa o lugar dela', () => {
    expect(rotas(itensVisiveis(['MORADOR']))).not.toContain('/gestao/unidades')
    expect(rotas(itensDoCelular(['MORADOR']))).toEqual(['/', '/debitos', '/documentos', '/balancete', '/conta'])
  })

  it('síndico e conselheiro veem a gestão; no celular, a aba Gestão', () => {
    for (const papel of ['SINDICO', 'CONSELHEIRO']) {
      expect(rotas(itensVisiveis([papel]))).toEqual(expect.arrayContaining(['/gestao/unidades', '/gestao/moradores', '/gestao/mandatos']))
      expect(rotas(itensDoCelular([papel]))).toEqual(['/', '/debitos', '/documentos', '/gestao', '/conta'])
    }
  })

  it('a barra do celular nunca passa de 5 itens', () => {
    expect(itensDoCelular(['SINDICO', 'MORADOR', 'ADMINISTRADORA']).length).toBeLessThanOrEqual(5)
  })

  it('só o síndico e a administradora veem a assinatura do Condomix', () => {
    expect(rotas(itensVisiveis(['SINDICO']))).toContain('/gestao/assinatura')
    expect(rotas(itensVisiveis(['CONSELHEIRO']))).not.toContain('/gestao/assinatura')
  })

  it('com o condomínio suspenso por falta de pagamento, sobram a assinatura e a conta', () => {
    expect(rotas(itensVisiveis(['SINDICO', 'MORADOR'], true))).toEqual(['/gestao/assinatura', '/conta'])
    expect(rotas(itensDoCelular(['SINDICO', 'MORADOR'], true))).toEqual(['/gestao/assinatura', '/conta'])
  })
})
