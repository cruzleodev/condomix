import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { guardar } from '../sessao/armazenamento'
import { SessaoProvider } from '../sessao/SessaoProvider'
import { tokenDeTeste } from '../testes/tokens'
import { hojeIso, mesAtual, periodoDoMes } from '../util/formato'
import { AgendaDoEspaco } from './AgendaDoEspaco'

const TOKEN = tokenDeTeste({ sub: 'm', email: 'ana@teste.com', condominio: 'c1', perfis: ['MORADOR'], exp: 2_000_000_000 })

const SALAO = {
  id: 'e1', nome: 'Salão de festas', valorAluguel: 150, ativo: true,
  regras: { antecedenciaMinimaDias: 0, antecedenciaMaximaDias: 90, bloqueiaInadimplente: true, diasParaCancelar: 2 },
  horarios: [{ nome: 'Noite', inicio: '18:00:00', fim: '23:00:00' }],
}

describe('agenda do espaço: reservar um horário livre', () => {
  beforeEach(() => guardar({ token: TOKEN, condominios: [{ id: 'c1', nome: 'Residencial Azul', perfis: ['MORADOR'] }] }))
  afterEach(() => vi.restoreAllMocks())

  it('escolhe o horário, vê o aluguel e o prazo, confirma e recebe a confirmação', async () => {
    const [, ultimo] = periodoDoMes(mesAtual())
    const dia = ultimo // o último dia do mês atual, sempre hoje ou no futuro
    const pedidos: { caminho: string; corpo?: unknown }[] = []
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (pedido) => {
      const r = pedido as Request
      const caminho = new URL(r.url).pathname
      const corpo = r.method === 'POST' ? await r.json() : undefined
      pedidos.push({ caminho, corpo })
      const respostas: Record<string, unknown> = {
        '/api/espacos/e1': SALAO,
        '/api/espacos/e1/agenda': [
          { data: dia, horarios: [{ nome: 'Noite', inicio: '18:00:00', fim: '23:00:00', situacao: 'LIVRE' }] },
        ],
        '/api/reservas/unidades': [{ id: 'u1', descricao: 'A-101' }],
        '/api/reservas': { id: 'r1', data: dia, horario: 'Noite', inicio: '18:00:00', fim: '23:00:00', valor: 150, situacao: 'CONFIRMADA' },
      }
      return new Response(JSON.stringify(respostas[caminho]), { status: caminho === '/api/reservas' ? 201 : 200, headers: { 'Content-Type': 'application/json' } })
    })
    const roteador = createMemoryRouter([{ path: '/reservas/:id', element: <AgendaDoEspaco /> }], { initialEntries: ['/reservas/e1'] })
    render(<QueryClientProvider client={new QueryClient()}><SessaoProvider><RouterProvider router={roteador} /></SessaoProvider></QueryClientProvider>)

    await userEvent.click(await screen.findByRole('button', { name: 'Noite (18h às 23h)' }))
    expect(await screen.findByText('Para a unidade A-101.')).toBeInTheDocument()
    expect(screen.getByText(/vira uma cobrança da unidade/)).toHaveTextContent('R$ 150,00')
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar a reserva' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Reservado: Salão de festas')
    expect(screen.getByRole('status')).toHaveTextContent('O aluguel de R$ 150,00 já está em Meus débitos.')
    expect(pedidos.find((p) => p.caminho === '/api/reservas')?.corpo).toMatchObject({ espacoId: 'e1', data: dia, horario: 'Noite', unidadeId: 'u1' })
    expect(dia >= hojeIso()).toBe(true)
  })
})
