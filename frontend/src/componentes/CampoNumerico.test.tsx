import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { dinheiroNoCampo, filtrarDecimal, lerReais, mascaraDeDinheiro } from '../util/formato'
import { CampoDinheiro, CampoNumero } from './CampoNumerico'

function Dinheiro({ inicial = '' }: { inicial?: string }) {
  const [valor, setValor] = useState(inicial)
  return <><CampoDinheiro rotulo="Valor (R$)" valor={valor} aoMudar={setValor} /><output>{String(lerReais(valor))}</output></>
}

function Percentual() {
  const [valor, setValor] = useState('')
  return <form aria-label="f"><CampoNumero rotulo="Multa (%)" casas={2} max={2} valor={valor} aoMudar={setValor} /></form>
}

describe('campos de número', () => {
  it('máscaras de dinheiro e de número com vírgula', () => {
    expect(mascaraDeDinheiro('15000')).toBe('150,00')
    expect(mascaraDeDinheiro('a1b2c3456')).toBe('1.234,56')
    expect(mascaraDeDinheiro('000')).toBe('')
    expect(dinheiroNoCampo('150.5')).toBe('150,50')
    expect(filtrarDecimal('2.5')).toBe('2,5')
    expect(filtrarDecimal('1,2,3456x')).toBe('1,23')
    expect(filtrarDecimal('abc')).toBe('')
  })

  it('dinheiro: letras não entram e o valor sai pronto para a API', async () => {
    render(<Dinheiro />)
    const campo = screen.getByLabelText('Valor (R$)')
    await userEvent.type(campo, 'asd12e3456')
    expect(campo).toHaveValue('1.234,56')
    expect(screen.getByText('1234.56', { selector: 'output' })).toBeInTheDocument()
  })

  it('dinheiro: mostra formatado o valor que veio da API', () => {
    render(<Dinheiro inicial="150.5" />)
    expect(screen.getByLabelText('Valor (R$)')).toHaveValue('150,50')
  })

  it('percentual: só números e vírgula, e avisa acima do máximo', async () => {
    render(<Percentual />)
    const campo = screen.getByLabelText('Multa (%)')
    await userEvent.type(campo, 'x2.5')
    expect(campo).toHaveValue('2,5')
    expect(screen.getByText('O máximo é 2.')).toBeInTheDocument()
    expect(campo).toBeInvalid()
  })
})
