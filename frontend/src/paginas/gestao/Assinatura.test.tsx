import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { RotaDoCondominio } from '../../layout/Rotas'
import { guardar } from '../../sessao/armazenamento'
import { SessaoProvider } from '../../sessao/SessaoProvider'
import { tokenDeTeste } from '../../testes/tokens'
import { Assinatura } from './Assinatura'

const TOKEN = tokenDeTeste({ sub: 's', email: 'sindico@teste.com', condominio: 'c1', perfis: ['SINDICO'], exp: 2_000_000_000 })

const ATRASADA = {
  id: 'f1', periodoInicio: '2026-10-19', periodoFim: '2026-11-19', valor: 199.9, vencimento: '2026-10-26',
  situacao: 'EMITIDA', atrasada: true, suspensaoEm: '2026-11-11',
}

const SUSPENSA = {
  plano: 'Completo', periodicidade: 'MENSAL', status: 'ATIVA', preco: 199.9, suspensaPorFaltaDePagamento: true, faturas: [ATRASADA],
}

/** Responde cada endereço da API com o corpo indicado (o corpo pode mudar a cada chamada). */
function api(respostas: Record<string, () => unknown>) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (pedido) => {
    const caminho = new URL((pedido as Request).url).pathname
    return new Response(JSON.stringify(respostas[caminho]?.()), { status: 200, headers: { 'Content-Type': 'application/json' } })
  })
}

function montar(inicio: string) {
  const roteador = createMemoryRouter([
    { element: <RotaDoCondominio />, children: [
      { path: '/', element: <p>Início do condomínio</p> },
      { path: '/gestao/cobrancas', element: <p>Cobranças</p> },
      { path: '/gestao/assinatura', element: <Assinatura /> },
    ] },
  ], { initialEntries: [inicio] })
  render(
    <QueryClientProvider client={new QueryClient()}>
      <SessaoProvider><RouterProvider router={roteador} /></SessaoProvider>
    </QueryClientProvider>,
  )
  return roteador
}

describe('assinatura do Condomix: condomínio suspenso por falta de pagamento', () => {
  beforeEach(() => guardar({
    token: TOKEN,
    condominios: [{ id: 'c1', nome: 'Residencial Azul', perfis: ['SINDICO'], pagamentoPendente: true }],
  }))
  afterEach(() => vi.restoreAllMocks())

  it('qualquer tela leva à assinatura, que mostra a suspensão e o Pix da fatura atrasada', async () => {
    api({
      '/api/assinatura': () => SUSPENSA,
      '/api/assinatura/faturas/f1/pagamento': () => ({
        valor: 199.9, vencimento: '2026-10-26', gateway: 'ASAAS', linhaDigitavel: '00190.00009', pixCopiaECola: '00020126BR.GOV.BCB.PIX',
      }),
    })
    const roteador = montar('/gestao/cobrancas')

    expect(await screen.findByText('O acesso ao Condomix está suspenso por falta de pagamento.')).toBeInTheDocument()
    expect(roteador.state.location.pathname).toBe('/gestao/assinatura')
    // A tabela tem a versão do computador e a do celular: o texto aparece nas duas.
    expect(screen.getAllByText('Venceu em 26/10/2026').length).toBeGreaterThan(0)
    expect(screen.getAllByText('19/10/2026 a 18/11/2026').length).toBeGreaterThan(0)
    expect(screen.getByText('Pagar a fatura de 19/10/2026 a 18/11/2026')).toBeInTheDocument()
    // A fatura atrasada já vem aberta, com o Pix.
    expect(await screen.findByText('00020126BR.GOV.BCB.PIX')).toBeInTheDocument()
  })

  it('confirmado o pagamento, troca o acesso restrito pelo normal e volta ao início', { timeout: 12_000 }, async () => {
    let pago = false
    const chamadas = api({
      '/api/assinatura': () => (pago ? { ...SUSPENSA, suspensaPorFaltaDePagamento: false, faturas: [{ ...ATRASADA, situacao: 'PAGA', atrasada: false }] } : SUSPENSA),
      '/api/assinatura/faturas/f1/pagamento': () => ({ valor: 199.9, vencimento: '2026-10-26' }),
      '/api/auth/condominio': () => ({
        token: TOKEN, condominioId: 'c1',
        condominios: [{ id: 'c1', nome: 'Residencial Azul', perfis: ['SINDICO'], pagamentoPendente: false }],
      }),
    })
    const roteador = montar('/gestao/assinatura')
    await screen.findByText('O acesso ao Condomix está suspenso por falta de pagamento.')

    pago = true // a tela confere de novo a cada 5 segundos

    expect(await screen.findByText('Início do condomínio', {}, { timeout: 8000 })).toBeInTheDocument()
    expect(roteador.state.location.pathname).toBe('/')
    expect(chamadas.mock.calls.some(([pedido]) => new URL((pedido as Request).url).pathname === '/api/auth/condominio')).toBe(true)
  })
})
