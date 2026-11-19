import { describe, expect, it } from 'vitest'
import { competencia, data, mesAtual, moeda, tamanhoDeArquivo, periodoDaFatura } from './formato'

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

import { documento, faixa, percentual, separarIdentificacoes, telefone, umAnoDepoisMenosUmDia } from './formato'

describe('formato da gestão', () => {
  it('separa a lista do cadastro em lote e gera sequências', () => {
    expect(separarIdentificacoes('101, 102\n201 202;101')).toEqual(['101', '102', '201', '202'])
    expect(faixa(101, 104)).toEqual(['101', '102', '103', '104'])
    expect(faixa(10, 5)).toEqual([])
  })

  it('mostra fração em %, telefone e documento formatados', () => {
    expect(percentual(0.025)).toBe('2,5%')
    expect(telefone('5591999998888')).toBe('(91) 99999-8888')
    expect(documento('52998224725')).toBe('529.982.247-25')
    expect(documento('12ABC34501DE35')).toBe('12.ABC.345/01DE-35')
  })

  it('calcula o fim de um mandato de 1 ano, inclusive em ano bissexto', () => {
    expect(umAnoDepoisMenosUmDia('2026-11-17')).toBe('2027-11-16')
    expect(umAnoDepoisMenosUmDia('2028-03-01')).toBe('2029-02-28')
  })
})

import { lerReais, periodoDoMes, proximoMes } from './formato'

describe('formato do financeiro', () => {
  it('lê valores digitados em reais', () => {
    expect(lerReais('1.234,56')).toBe(1234.56)
    expect(lerReais('1234.56')).toBe(1234.56)
    expect(lerReais('R$ 850')).toBe(850)
    expect(lerReais('')).toBeUndefined()
    expect(lerReais('abc')).toBeUndefined()
  })

  it('calcula o período do mês e o mês seguinte', () => {
    expect(periodoDoMes('2028-02')).toEqual(['2028-02-01', '2028-02-29'])
    expect(proximoMes('2026-12')).toBe('2027-01')
  })

  it('mostra o período da fatura até o dia anterior ao fim', () => {
    expect(periodoDaFatura('2026-11-19', '2026-12-19')).toBe('19/11/2026 a 18/12/2026')
    expect(periodoDaFatura('2026-12-01', '2027-01-01')).toBe('01/12/2026 a 31/12/2026')
    expect(periodoDaFatura(null, '2027-01-01')).toBe('—')
  })
})
