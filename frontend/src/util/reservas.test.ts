import { describe, expect, it } from 'vitest'
import { semanasDoMes } from './formato'
import { situacaoDoDia } from './reservas'

describe('calendário de reservas', () => {
  it('resume os horários do dia numa cor', () => {
    expect(situacaoDoDia([{ situacao: 'LIVRE' }, { situacao: 'LIVRE' }])).toBe('LIVRE')
    expect(situacaoDoDia([{ situacao: 'LIVRE' }, { situacao: 'OCUPADO' }])).toBe('PARCIAL')
    expect(situacaoDoDia([{ situacao: 'LIVRE' }, { situacao: 'DA_MINHA_UNIDADE' }])).toBe('PARCIAL')
    expect(situacaoDoDia([{ situacao: 'DA_MINHA_UNIDADE' }, { situacao: 'OCUPADO' }])).toBe('MINHA')
    expect(situacaoDoDia([{ situacao: 'OCUPADO' }, { situacao: 'INDISPONIVEL' }])).toBe('OCUPADO')
    expect(situacaoDoDia([{ situacao: 'INDISPONIVEL' }])).toBe('INDISPONIVEL')
    expect(situacaoDoDia([])).toBe('INDISPONIVEL')
  })

  it('monta as semanas do mês de domingo a sábado', () => {
    const novembro = semanasDoMes('2026-11') // 01/11/2026 é domingo
    expect(novembro[0]).toEqual(['2026-11-01', '2026-11-02', '2026-11-03', '2026-11-04', '2026-11-05', '2026-11-06', '2026-11-07'])
    expect(novembro.at(-1)).toEqual(['2026-11-29', '2026-11-30', null, null, null, null, null])
    const fevereiro = semanasDoMes('2027-02') // 01/02/2027 é segunda
    expect(fevereiro[0][0]).toBeNull()
    expect(fevereiro[0][1]).toBe('2027-02-01')
    expect(fevereiro.flat().filter(Boolean)).toHaveLength(28)
  })
})
