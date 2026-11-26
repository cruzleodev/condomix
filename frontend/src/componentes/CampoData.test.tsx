import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { dataParaIso, mascaraDeData } from '../util/formato'
import { CampoData } from './CampoData'

function Exemplo({ inicial = '', max }: { inicial?: string; max?: string }) {
  const [valor, setValor] = useState(inicial)
  return <><CampoData rotulo="Posse" valor={valor} aoMudar={setValor} max={max} required /><output>{valor || 'vazio'}</output></>
}

describe('campo de data', () => {
  it('aceita só números e põe as barras', () => {
    expect(mascaraDeData('23112026')).toBe('23/11/2026')
    expect(mascaraDeData('2a3/1')).toBe('23/1')
    expect(dataParaIso('31/02/2026')).toBe('')
    expect(dataParaIso('29/02/2028')).toBe('2028-02-29')
  })

  it('digitando, guarda a data no formato da API; data que não existe avisa', async () => {
    render(<Exemplo />)
    const campo = screen.getByLabelText('Posse')
    await userEvent.type(campo, 'abc23112026')
    expect(campo).toHaveValue('23/11/2026')
    expect(screen.getByText('2026-11-23', { selector: 'output' })).toBeInTheDocument()
    await userEvent.clear(campo)
    await userEvent.type(campo, '31022026')
    expect(screen.getByText('Esta data não existe.')).toBeInTheDocument()
    expect(campo).toBeInvalid()
  })

  it('escolhe no calendário, respeitando o limite', async () => {
    render(<Exemplo inicial="2026-11-10" max="2026-11-20" />)
    await userEvent.click(screen.getByRole('button', { name: 'Abrir o calendário: Posse' }))
    expect(screen.getByRole('button', { name: '21/11/2026' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: '15/11/2026' }))
    expect(screen.getByLabelText('Posse')).toHaveValue('15/11/2026')
    expect(screen.getByText('2026-11-15', { selector: 'output' })).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
