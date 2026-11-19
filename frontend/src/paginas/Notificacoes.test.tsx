import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { PreferenciasDeNotificacao } from '../componentes/PreferenciasDeNotificacao'
import { guardar } from '../sessao/armazenamento'
import { SessaoProvider } from '../sessao/SessaoProvider'
import { tokenDeTeste } from '../testes/tokens'
import { Notificacoes } from './Notificacoes'

const MORADORA = tokenDeTeste({ sub: 'a', email: 'ana@teste.com', condominio: 'c1', perfis: ['MORADOR'], exp: 2_000_000_000 })

const GRUPOS = [
  { grupo: 'COBRANCAS', rotulo: 'Cobranças', exemplos: 'boleto disponível', email: true, push: true },
  { grupo: 'RESERVAS', rotulo: 'Reservas', exemplos: 'reserva confirmada', email: true, push: true },
  { grupo: 'ASSINATURA', rotulo: 'Assinatura do Condomix', exemplos: 'fatura', email: true, push: true },
  { grupo: 'DOCUMENTOS', rotulo: 'Documentos', exemplos: 'documento novo', email: true, push: true },
]

function api(respostas: Record<string, unknown>) {
  const pedidos: { metodo: string; caminho: string; corpo?: unknown }[] = []
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (pedido) => {
    const r = pedido as Request
    const caminho = new URL(r.url).pathname
    const corpo = r.method === 'GET' ? undefined : await r.text().then((t) => (t ? JSON.parse(t) : undefined))
    pedidos.push({ metodo: r.method, caminho, corpo })
    const resposta = respostas[`${r.method} ${caminho}`]
    return resposta === undefined ? new Response(null, { status: 204 })
      : new Response(JSON.stringify(resposta), { status: 200, headers: { 'Content-Type': 'application/json' } })
  })
  return pedidos
}

function montar(elemento: React.ReactNode, inicio = '/') {
  const roteador = createMemoryRouter([
    { path: '/', element: elemento },
    { path: '/reservas', element: <p>Tela de reservas</p> },
  ], { initialEntries: [inicio] })
  render(<QueryClientProvider client={new QueryClient()}><SessaoProvider><RouterProvider router={roteador} /></SessaoProvider></QueryClientProvider>)
  return roteador
}

describe('notificações', () => {
  beforeEach(() => guardar({ token: MORADORA, condominios: [{ id: 'c1', nome: 'Residencial Azul', perfis: ['MORADOR'] }] }))
  afterEach(() => vi.restoreAllMocks())

  it('tocar numa não lida marca como lida e abre a tela do assunto', async () => {
    const pedidos = api({
      'GET /api/notificacoes': [
        { id: 'n1', grupo: 'RESERVAS', titulo: 'Reserva confirmada', texto: 'Salão de festas, sábado.', link: '/reservas', criadoEm: '2026-11-19T12:00:00Z', lida: false },
      ],
    })
    const roteador = montar(<Notificacoes />)
    await userEvent.click(await screen.findByRole('button', { name: /Reserva confirmada/ }))

    expect(await screen.findByText('Tela de reservas')).toBeInTheDocument()
    expect(roteador.state.location.pathname).toBe('/reservas')
    expect(pedidos.some((p) => p.metodo === 'POST' && p.caminho === '/api/notificacoes/n1/leitura')).toBe(true)
  })

  it('morador não vê o grupo da assinatura; desmarcar o e-mail grava a escolha', async () => {
    const pedidos = api({
      'GET /api/notificacoes/preferencias': { grupos: GRUPOS, emailLigado: true, aparelhoComPush: false },
      'PUT /api/notificacoes/preferencias': { grupos: GRUPOS, emailLigado: true, aparelhoComPush: false },
    })
    montar(<PreferenciasDeNotificacao />)
    const emailDasCobrancas = await screen.findByRole('checkbox', { name: 'Cobranças por e-mail' })
    expect(screen.queryByText('Assinatura do Condomix')).not.toBeInTheDocument()

    await userEvent.click(emailDasCobrancas)
    const put = pedidos.find((p) => p.metodo === 'PUT')
    expect(put?.corpo).toEqual(expect.arrayContaining([{ grupo: 'COBRANCAS', email: false, push: true }]))
    // Sem service worker (como nos testes e em desenvolvimento), o app explica por que o push não liga.
    expect(screen.getByText(/O push funciona com o app instalado|Este navegador não recebe/)).toBeInTheDocument()
  })
})
