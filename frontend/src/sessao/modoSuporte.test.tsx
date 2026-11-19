import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { RotaDoCondominio, RotaLogada } from '../layout/Rotas'
import { tokenDeTeste } from '../testes/tokens'
import { avisarSessaoExpirada, guardar } from './armazenamento'
import { useSessao } from './contexto'
import { SessaoProvider } from './SessaoProvider'

const DO_ADMIN = tokenDeTeste({ sub: 'a', email: 'admin@teste.com', perfis: ['ADMIN_PLATAFORMA'], exp: 2_000_000_000 })
const DE_SUPORTE = tokenDeTeste({ sub: 'a', email: 'admin@teste.com', condominio: 'c1', perfis: ['SUPORTE'], suporte: 's1', exp: 2_000_000_000 })

function Painel() {
  const { entrarComoSuporte, condominio, perfis } = useSessao()
  return (
    <>
      <p>Painel da plataforma ({perfis.join(',')}){condominio ? ` em ${condominio.nome}` : ''}</p>
      <button onClick={() => entrarComoSuporte(DE_SUPORTE, { id: 'c1', nome: 'Residencial Azul' })}>Entrar como suporte</button>
    </>
  )
}

function Condominio() {
  const { condominio, suporte, sairDoSuporte } = useSessao()
  return (
    <>
      <p>Dentro de {condominio?.nome}{suporte ? ' (suporte)' : ''}</p>
      <button onClick={sairDoSuporte}>Voltar ao painel</button>
    </>
  )
}

function montar(inicio: string) {
  const roteador = createMemoryRouter([
    { element: <RotaLogada />, children: [
      { path: '/plataforma', element: <Painel /> },
      { path: '/condominio', element: <p>Escolha do condomínio</p> },
      { path: '/sem-acesso', element: <p>Sem acesso</p> },
    ] },
    { element: <RotaDoCondominio />, children: [{ path: '/', element: <Condominio /> }] },
    { path: '/entrar', element: <p>Login</p> },
  ], { initialEntries: [inicio] })
  render(<SessaoProvider><RouterProvider router={roteador} /></SessaoProvider>)
  return roteador
}

describe('modo suporte e destino de quem não tem condomínio escolhido', () => {
  beforeEach(() => guardar(null))
  afterEach(() => vi.restoreAllMocks())

  it('o administrador sem condomínio vai para o painel da plataforma', async () => {
    guardar({ token: DO_ADMIN, condominios: [] })
    montar('/')
    expect(await screen.findByText(/Painel da plataforma/)).toBeInTheDocument()
  })

  it('quem não tem nenhum condomínio vê "sem acesso"; quem tem vários, a escolha', async () => {
    guardar({ token: tokenDeTeste({ sub: 'm', email: 'm@teste.com', perfis: [], exp: 2_000_000_000 }), condominios: [] })
    montar('/')
    expect(await screen.findByText('Sem acesso')).toBeInTheDocument()
  })

  it('entra como suporte e, ao voltar, encerra o acesso no servidor com o token do administrador', async () => {
    const chamadas = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 204 }))
    guardar({ token: DO_ADMIN, condominios: [] })
    const roteador = montar('/plataforma')
    await userEvent.click(await screen.findByRole('button', { name: 'Entrar como suporte' }))
    await act(() => roteador.navigate('/'))
    expect(await screen.findByText('Dentro de Residencial Azul (suporte)')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Voltar ao painel' }))
    expect(await screen.findByText('Painel da plataforma (ADMIN_PLATAFORMA)')).toBeInTheDocument()
    const pedido = chamadas.mock.calls[0][0] as Request
    expect(pedido.method).toBe('POST')
    expect(new URL(pedido.url).pathname).toBe('/api/plataforma/suporte/s1/encerramento')
    expect(pedido.headers.get('Authorization')).toBe(`Bearer ${DO_ADMIN}`)
  })

  it('quando o token de suporte vence, volta ao administrador em vez de deslogar', async () => {
    guardar({ token: DE_SUPORTE, condominios: [{ id: 'c1', nome: 'Residencial Azul', perfis: ['SUPORTE'] }], anterior: { token: DO_ADMIN, condominios: [] } })
    montar('/')
    expect(await screen.findByText('Dentro de Residencial Azul (suporte)')).toBeInTheDocument()
    act(() => avisarSessaoExpirada())
    expect(await screen.findByText('Painel da plataforma (ADMIN_PLATAFORMA)')).toBeInTheDocument()
  })
})
