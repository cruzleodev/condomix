import type { CondominioDisponivel } from '../api/tipos'

/**
 * Onde a sessão fica guardada no aparelho (localStorage, para o app continuar logado ao reabrir).
 * Todo acesso é protegido: em navegação anônima ou com armazenamento bloqueado, o app funciona sem guardar.
 */
const CHAVE = 'condomix.sessao'

export interface SessaoGuardada {
  token: string
  condominios: CondominioDisponivel[]
}

let ouvinteDeExpiracao: (() => void) | null = null

export function guardar(sessao: SessaoGuardada | null): void {
  try {
    if (sessao) localStorage.setItem(CHAVE, JSON.stringify(sessao))
    else localStorage.removeItem(CHAVE)
  } catch {
    /* armazenamento indisponível: segue sem guardar */
  }
  memoria = sessao
}

let memoria: SessaoGuardada | null = ler()

function ler(): SessaoGuardada | null {
  try {
    const texto = localStorage.getItem(CHAVE)
    return texto ? (JSON.parse(texto) as SessaoGuardada) : null
  } catch {
    return null
  }
}

export function sessaoGuardada(): SessaoGuardada | null {
  return memoria
}

export function tokenAtual(): string | null {
  return memoria?.token ?? null
}

/** A tela de sessão se inscreve aqui para voltar ao login quando a API disser que o token venceu. */
export function aoExpirarSessao(ouvinte: (() => void) | null): void {
  ouvinteDeExpiracao = ouvinte
}

export function avisarSessaoExpirada(): void {
  guardar(null)
  ouvinteDeExpiracao?.()
}
