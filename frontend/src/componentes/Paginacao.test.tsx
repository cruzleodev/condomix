import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Paginacao } from './Paginacao'

describe('paginação', () => {
  it('troca o tamanho (10, 20, 50 ou todas) e navega entre as páginas', async () => {
    const aoMudarTamanho = vi.fn()
    const aoMudarPagina = vi.fn()
    render(<Paginacao pagina={0} totalPaginas={3} total={25} tamanho={10} aoMudarPagina={aoMudarPagina}
      aoMudarTamanho={aoMudarTamanho} rotuloDoTotal={(n) => `${n} cobranças`} />)
    expect(screen.getByText('· 25 cobranças')).toBeInTheDocument()
    expect(screen.getByText('Página 1 de 3')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Próxima' }))
    expect(aoMudarPagina).toHaveBeenCalledWith(1)
    expect(screen.getAllByRole('option').map((o) => o.textContent)).toEqual(['10', '20', '50', 'Todas'])
    await userEvent.selectOptions(screen.getByLabelText('Linhas por página'), 'Todas')
    expect(aoMudarTamanho).toHaveBeenCalledWith(1000)
  })
})
