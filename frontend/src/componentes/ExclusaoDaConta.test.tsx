import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { guardar, tokenAtual } from '../sessao/armazenamento'
import { SessaoProvider } from '../sessao/SessaoProvider'
import { tokenDeTeste } from '../testes/tokens'
import { ExclusaoDaConta } from './ExclusaoDaConta'

const TOKEN = tokenDeTeste({ sub: 'a', email: 'ana@teste.com', condominio: 'c1', perfis: ['MORADOR'], exp: 2_000_000_000 })

function montar() {
  render(<QueryClientProvider client={new QueryClient()}><SessaoProvider><ExclusaoDaConta /></SessaoProvider></QueryClientProvider>)
}

describe('excluir minha conta', () => {
  beforeEach(() => guardar({ token: TOKEN, condominios: [{ id: 'c1', nome: 'Residencial Azul', perfis: ['MORADOR'] }] }))
  afterEach(() => vi.restoreAllMocks())

  it('explica o que fica com o condomínio, pede a senha e, excluída, encerra a sessão', async () => {
    const pedidos: unknown[] = []
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (pedido) => {
      pedidos.push(await (pedido as Request).json())
      return new Response(null, { status: 204 })
    })
    montar()
    await userEvent.click(screen.getByRole('button', { name: 'Excluir minha conta' }))
    expect(screen.getByText(/ficam com o condomínio/)).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Sua senha, para confirmar'), 'minha-senha')
    await userEvent.click(screen.getByRole('button', { name: 'Excluir minha conta para sempre' }))

    await vi.waitFor(() => expect(tokenAtual()).toBeNull())
    expect(pedidos).toEqual([{ senha: 'minha-senha' }])
  })

  it('senha errada mostra o motivo e mantém a sessão', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ detail: 'A senha não confere.' }),
      { status: 400, headers: { 'Content-Type': 'application/problem+json' } }))
    montar()
    await userEvent.click(screen.getByRole('button', { name: 'Excluir minha conta' }))
    await userEvent.type(screen.getByLabelText('Sua senha, para confirmar'), 'errada')
    await userEvent.click(screen.getByRole('button', { name: 'Excluir minha conta para sempre' }))

    expect(await screen.findByText('A senha não confere.')).toBeInTheDocument()
    expect(tokenAtual()).toBe(TOKEN)
  })
})
