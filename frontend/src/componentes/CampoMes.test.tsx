import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { CampoMes } from './CampoMes'

function Exemplo({ inicial, max, permiteVazio }: { inicial: string; max?: string; permiteVazio?: boolean }) {
  const [mes, setMes] = useState(inicial)
  return <><CampoMes rotulo="Mês" valor={mes} aoMudar={setMes} max={max} permiteVazio={permiteVazio} /><output>{mes || 'vazio'}</output></>
}

describe('campo de mês', () => {
  it('troca o mês pelas setas e não passa do máximo', async () => {
    render(<Exemplo inicial="2026-10" max="2026-11" />)
    await userEvent.click(screen.getByRole('button', { name: 'Próximo mês' }))
    expect(screen.getByText('2026-11', { selector: 'output' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Próximo mês' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Mês anterior' }))
    expect(screen.getByText('2026-10', { selector: 'output' })).toBeInTheDocument()
  })

  it('escolhe o mês no quadro, troca o ano e permite "todos os meses" quando é filtro', async () => {
    render(<Exemplo inicial="" permiteVazio />)
    await userEvent.click(screen.getByRole('button', { name: /Todos os meses/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Próximo ano' }))
    const ano = Number(screen.getByRole('dialog').querySelector('span')?.textContent)
    await userEvent.click(screen.getByRole('button', { name: new RegExp(`^março de ${ano}$`, "i") }))
    expect(screen.getByText(`${ano}-03`, { selector: 'output' })).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /março/i }))
    await userEvent.click(screen.getByRole('button', { name: 'Todos os meses' }))
    expect(screen.getByText('vazio', { selector: 'output' })).toBeInTheDocument()
  })
})
