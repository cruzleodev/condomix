import { describe, expect, it } from 'vitest'
import { competencia, data, mesAtual, moeda, tamanhoDeArquivo } from './formato'

describe('formato', () => {
  it('formata reais no padrão brasileiro', () => {
    expect(moeda(1234.5).replace(/\s/g, ' ')).toBe('R$ 1.234,50')
    expect(moeda(undefined)).toBe('—')
  })

  it('formata datas da API sem mudar o dia por causa do fuso', () => {
    expect(data('2026-12-01')).toBe('01/12/2026')
    expect(data(null)).toBe('—')
  })

  it('formata competência e o mês atual', () => {
    expect(competencia('2026-12')).toBe('dez/2026')
    expect(mesAtual(new Date(2026, 0, 31))).toBe('2026-01')
  })

  it('mostra o tamanho do arquivo', () => {
    expect(tamanhoDeArquivo(2048)).toBe('2 KB')
    expect(tamanhoDeArquivo(1572864)).toBe('1,5 MB')
  })
})
