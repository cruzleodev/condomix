import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SessaoProvider } from '../sessao/SessaoProvider'
import { tokenDeTeste } from '../testes/tokens'
import { Entrar } from './Entrar'

function montar() {
  const roteador = createMemoryRouter([
    { path: '/entrar', element: <Entrar /> },
    { path: '/', element: <p>Tela inicial</p> },
    { path: '/condominio', element: <p>Escolha do condomínio</p> },
  ], { initialEntries: ['/entrar'] })
  render(
    <QueryClientProvider client={new QueryClient()}>
      <SessaoProvider><RouterProvider router={roteador} /></SessaoProvider>
    </QueryClientProvider>,
  )
}

function respostaDaApi(status: number, corpo: unknown) {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue(
    new Response(JSON.stringify(corpo), { status, headers: { 'Content-Type': 'application/json' } }))
}

async function preencherEEntrar() {
  await userEvent.type(screen.getByLabelText('E-mail'), 'ana@teste.com')
  await userEvent.type(screen.getByLabelText('Senha'), 'senha-da-ana')
  await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))
}

describe('Entrar', () => {
  afterEach(() => vi.restoreAllMocks())

  it('com um condomínio só, vai direto para o início', async () => {
    const token = tokenDeTeste({ sub: '1', email: 'ana@teste.com', condominio: 'c1', perfis: ['MORADOR'], exp: 2_000_000_000 })
    respostaDaApi(200, { token, condominioId: 'c1', condominios: [{ id: 'c1', nome: 'Residencial', perfis: ['MORADOR'] }] })
    montar()
    await preencherEEntrar()
    expect(await screen.findByText('Tela inicial')).toBeInTheDocument()
  })

  it('com vários condomínios, pede para escolher', async () => {
    const token = tokenDeTeste({ sub: '1', email: 'sindico@teste.com', perfis: [], exp: 2_000_000_000 })
    respostaDaApi(200, { token, condominios: [{ id: 'a', nome: 'A' }, { id: 'b', nome: 'B' }] })
    montar()
    await preencherEEntrar()
    expect(await screen.findByText('Escolha do condomínio')).toBeInTheDocument()
  })

  it('mostra a mensagem da API quando a senha está errada', async () => {
    respostaDaApi(401, { status: 401, detail: 'E-mail ou senha inválidos' })
    montar()
    await preencherEEntrar()
    expect(await screen.findByRole('alert')).toHaveTextContent('E-mail ou senha inválidos')
  })
})
