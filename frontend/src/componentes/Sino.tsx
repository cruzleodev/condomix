import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { api, dados } from '../api/cliente'

/** O sino com o número de notificações não lidas. Confere de novo a cada minuto e ao voltar para o app. */
export function Sino({ claro = false }: { claro?: boolean }) {
  const naoLidas = useQuery({
    queryKey: ['notificacoes', 'nao-lidas'],
    queryFn: async () => dados(await api.GET('/api/notificacoes/nao-lidas')),
    refetchInterval: 60_000,
  })
  const quantas = naoLidas.data?.quantidade ?? 0
  return (
    <Link to="/notificacoes" aria-label={quantas > 0 ? `Notificações: ${quantas} não lida(s)` : 'Notificações'}
      className={`relative inline-flex h-11 w-11 items-center justify-center rounded-full ${claro ? 'text-white hover:bg-white/10' : 'text-slate-700 hover:bg-slate-100'}`}>
      <svg aria-hidden viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
        <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
      </svg>
      {quantas > 0 && (
        <span className="absolute right-1 top-1 min-w-5 rounded-full bg-red-600 px-1 text-center text-xs font-bold leading-5 text-white">
          {quantas > 99 ? '99+' : quantas}
        </span>
      )}
    </Link>
  )
}
