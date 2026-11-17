import { createContext, useContext } from 'react'
import type { CondominioDisponivel } from '../api/tipos'

/** O que as telas sabem sobre quem está logado. */
export interface EstadoDaSessao {
  logado: boolean
  email: string | null
  /** Condomínio selecionado no token; nulo enquanto o usuário não escolheu. */
  condominioId: string | null
  condominio: CondominioDisponivel | null
  condominios: CondominioDisponivel[]
  perfis: string[]
  /** Verdadeiro quando a pessoa clicou em "Sair" (o próximo login pode ser de outra pessoa). */
  saiuPorVontade: boolean
  /** Login de e-mail e senha. Devolve "escolher" quando o usuário tem vários condomínios. */
  entrar: (email: string, senha: string) => Promise<'pronto' | 'escolher' | 'sem-acesso'>
  escolherCondominio: (condominioId: string) => Promise<void>
  sair: () => void
  tem: (...perfis: string[]) => boolean
}

export const Contexto = createContext<EstadoDaSessao | null>(null)

export function useSessao(): EstadoDaSessao {
  const valor = useContext(Contexto)
  if (!valor) throw new Error('useSessao precisa estar dentro de <SessaoProvider>')
  return valor
}
