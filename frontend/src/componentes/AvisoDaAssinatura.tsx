import { useQuery } from '@tanstack/react-query'
import { Link, useLocation } from 'react-router'
import { api, dados } from '../api/cliente'
import { useSessao } from '../sessao/contexto'
import { GESTAO } from '../sessao/papeis'
import { data } from '../util/formato'

/**
 * Para a gestão, no alto de todas as telas: a fatura do Condomix venceu e o acesso vai ser suspenso se não pagar.
 * Some na própria tela da assinatura, onde o aviso já está.
 */
export function AvisoDaAssinatura() {
  const { tem } = useSessao()
  const { pathname } = useLocation()
  const gestao = tem(...GESTAO)
  const assinatura = useQuery({
    queryKey: ['assinatura'],
    queryFn: async () => dados(await api.GET('/api/assinatura')),
    enabled: gestao,
    staleTime: 5 * 60_000,
  })
  const atrasada = assinatura.data?.faturas?.find((f) => f.atrasada)
  if (!gestao || !atrasada || pathname === '/gestao/assinatura') return null
  return (
    <div role="alert" className="mb-4 flex flex-col gap-2 rounded-xl bg-red-50 p-3 text-sm text-red-900 ring-1 ring-red-200 md:flex-row md:items-center md:justify-between">
      <p>
        A fatura do Condomix venceu em <strong>{data(atrasada.vencimento)}</strong>. Se não for paga, o acesso do condomínio
        será suspenso em <strong>{data(atrasada.suspensaoEm)}</strong>.
      </p>
      <Link to="/gestao/assinatura" className="shrink-0 font-semibold underline">Pagar agora</Link>
    </div>
  )
}
