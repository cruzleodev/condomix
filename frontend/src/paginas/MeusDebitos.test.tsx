import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MeusDebitos } from './MeusDebitos'

const COBRANCA = {
  id: 'c1', unidade: 'A-101', descricao: 'Taxa condominial 12/2026', competencia: '2026-12', vencimento: '2026-12-10',
  valor: 350, valorAtualizado: 350, status: 'ABERTA', vencida: false, diasDeAtraso: 0, itens: [],
}

/** Responde cada endereço da API com o corpo indicado. */
function api(respostas: Record<string, unknown>) {
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (pedido) => {
    const caminho = new URL((pedido as Request).url).pathname
    return new Response(JSON.stringify(respostas[caminho]), { status: 200, headers: { 'Content-Type': 'application/json' } })
  })
}

function montar() {
  render(<QueryClientProvider client={new QueryClient()}><MeusDebitos /></QueryClientProvider>)
}

describe('meus débitos: 2ª via com Pix e boleto', () => {
  afterEach(() => vi.restoreAllMocks())

  it('com o boleto emitido, mostra o Pix copia e cola e a linha digitável', async () => {
    api({
      '/api/eu/cobrancas': [{ ...COBRANCA, boleto: { situacao: 'EMITIDO', gateway: 'ASAAS' } }],
      '/api/eu/cobrancas/c1/pagamento': {
        cobrancaId: 'c1', valor: 350, vencimento: '2026-12-10', url: 'https://boleto.pdf', gateway: 'ASAAS',
        linhaDigitavel: '00190.00009 01234.567004', pixCopiaECola: '00020126580014BR.GOV.BCB.PIX', pixQrCode: 'iVBORw0KGgo=',
      },
    })
    montar()
    await userEvent.click(await screen.findByText('Pagar com Pix ou boleto ›'))

    expect(await screen.findByText('00020126580014BR.GOV.BCB.PIX')).toBeInTheDocument()
    expect(screen.getByText('00190.00009 01234.567004')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'QR Code do Pix' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Abrir o boleto em PDF' })).toHaveAttribute('href', 'https://boleto.pdf')
    expect(screen.getByText(/vence em/)).toHaveTextContent('vence em 10/12/2026')
  })

  it('com o boleto ainda sendo gerado, avisa para voltar em instantes', async () => {
    api({ '/api/eu/cobrancas': [{ ...COBRANCA, boleto: { situacao: 'PENDENTE', gateway: 'ASAAS' } }] })
    montar()
    expect(screen.queryByText('Pagar com Pix ou boleto ›')).not.toBeInTheDocument()
    await userEvent.click(await screen.findByText('Taxa condominial 12/2026'))
    expect(screen.getByText('O boleto está sendo gerado. Volte em instantes.')).toBeInTheDocument()
  })
})
