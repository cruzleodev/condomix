import { useQuery } from '@tanstack/react-query'
import { api, dados } from '../../api/cliente'
import type { AlteracaoDaAssinatura } from '../../api/tipos'
import { data, ROTULOS } from '../../util/formato'

/** Peças comuns às telas da plataforma. */

/** Os planos base ativos (Essencial, Completo), para escolher no cadastro e na troca de assinatura. */
export function usePlanosBase() {
  const planos = useQuery({ queryKey: ['plataforma', 'planos'], queryFn: async () => dados(await api.GET('/api/plataforma/planos')) })
  return (planos.data ?? []).filter((p) => p.tipo === 'BASE' && p.ativo)
}

export const COR_DO_STATUS = { TESTE: 'marca', ATIVA: 'verde', SUSPENSA: 'vermelho', CANCELADA: 'cinza' } as const

/** "Completo · Mensal · Em teste até 19/12/2026"; só o que o evento tem. */
export function descricaoDaAssinatura(h: AlteracaoDaAssinatura): string {
  const status = h.status && ROTULOS.statusDaAssinatura[h.status]
  const comPrazo = h.status === 'TESTE' ? `${status} ${h.fimDoTeste ? `até ${data(h.fimDoTeste)}` : 'sem prazo'}` : status
  const partes = [h.nomeDoPlano, h.periodicidade && ROTULOS.periodicidade[h.periodicidade], comPrazo]
  return partes.filter(Boolean).join(' · ') || '—'
}
