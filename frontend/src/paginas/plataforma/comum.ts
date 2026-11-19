import { useQuery } from '@tanstack/react-query'
import { api, dados } from '../../api/cliente'
import type { AlteracaoDaAssinatura } from '../../api/tipos'
import { ROTULOS } from '../../util/formato'

/** Peças comuns às telas da plataforma. */

/** Os planos base ativos (Essencial, Completo), para escolher no cadastro e na troca de assinatura. */
export function usePlanosBase() {
  const planos = useQuery({ queryKey: ['plataforma', 'planos'], queryFn: async () => dados(await api.GET('/api/plataforma/planos')) })
  return (planos.data ?? []).filter((p) => p.tipo === 'BASE' && p.ativo)
}

export const COR_DO_STATUS = { TESTE: 'marca', ATIVA: 'verde', SUSPENSA: 'vermelho', CANCELADA: 'cinza' } as const

/** "Completo · Mensal · Em teste"; só o que o evento tem. */
export function descricaoDaAssinatura(h: AlteracaoDaAssinatura): string {
  const partes = [h.nomeDoPlano, h.periodicidade && ROTULOS.periodicidade[h.periodicidade], h.status && ROTULOS.statusDaAssinatura[h.status]]
  return partes.filter(Boolean).join(' · ') || '—'
}
