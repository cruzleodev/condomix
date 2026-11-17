import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, dados } from '../api/cliente'
import type { Sessao } from '../api/tipos'
import { aoExpirarSessao, guardar, sessaoGuardada, type SessaoGuardada } from './armazenamento'
import { Contexto, type EstadoDaSessao } from './contexto'
import { expirado, lerToken } from './jwt'

function inicial(): SessaoGuardada | null {
  const guardada = sessaoGuardada()
  return guardada && !expirado(guardada.token) ? guardada : null
}

export function SessaoProvider({ children }: { children: ReactNode }) {
  const [sessao, setSessao] = useState<SessaoGuardada | null>(inicial)
  const [saiuPorVontade, setSaiuPorVontade] = useState(false)

  const atualizar = useCallback((nova: SessaoGuardada | null) => {
    guardar(nova)
    setSessao(nova)
  }, [])

  useEffect(() => {
    aoExpirarSessao(() => setSessao(null))
    return () => aoExpirarSessao(null)
  }, [])

  const entrar = useCallback(async (email: string, senha: string) => {
    const resposta: Sessao = dados(await api.POST('/api/auth/login', { body: { email, senha } }))
    setSaiuPorVontade(false)
    atualizar({ token: resposta.token ?? '', condominios: resposta.condominios ?? [] })
    if (resposta.condominioId) return 'pronto'
    return (resposta.condominios ?? []).length > 0 ? 'escolher' : 'sem-acesso'
  }, [atualizar])

  const escolherCondominio = useCallback(async (condominioId: string) => {
    const resposta: Sessao = dados(await api.POST('/api/auth/condominio', { body: { condominioId } }))
    atualizar({ token: resposta.token ?? '', condominios: resposta.condominios ?? [] })
  }, [atualizar])

  const sair = useCallback(() => {
    setSaiuPorVontade(true)
    atualizar(null)
  }, [atualizar])

  const valor = useMemo<EstadoDaSessao>(() => {
    const conteudo = sessao ? lerToken(sessao.token) : null
    const condominioId = conteudo?.condominio ?? null
    const perfis = conteudo?.perfis ?? []
    return {
      logado: !!conteudo,
      email: conteudo?.email ?? null,
      condominioId,
      condominio: sessao?.condominios.find((c) => c.id === condominioId) ?? null,
      condominios: sessao?.condominios ?? [],
      perfis,
      saiuPorVontade,
      entrar,
      escolherCondominio,
      sair,
      tem: (...procurados: string[]) => procurados.some((p) => perfis.includes(p)),
    }
  }, [sessao, saiuPorVontade, entrar, escolherCondominio, sair])

  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>
}
