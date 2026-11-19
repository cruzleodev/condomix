import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router'
import { api, dados } from '../api/cliente'
import type { Notificacao } from '../api/tipos'
import { Botao, Carregando, MensagemDeErro, TituloDaPagina, Vazio } from '../componentes/ui'
import { dataHora } from '../util/formato'

const ICONE = { COBRANCAS: 'R$', RESERVAS: '◷', ASSINATURA: '✦', DOCUMENTOS: '▤' } as const

/** As notificações deste condomínio: tocar abre a tela do assunto e marca como lida. */
export function Notificacoes() {
  const cache = useQueryClient()
  const navegar = useNavigate()
  const lista = useQuery({ queryKey: ['notificacoes', 'lista'], queryFn: async () => dados(await api.GET('/api/notificacoes')) })
  const atualizar = () => cache.invalidateQueries({ queryKey: ['notificacoes'] })
  const todas = useMutation({ mutationFn: async () => dados(await api.POST('/api/notificacoes/leitura')), onSuccess: atualizar })
  const ler = useMutation({
    mutationFn: async (id: string) => dados(await api.POST('/api/notificacoes/{id}/leitura', { params: { path: { id } } })),
    onSuccess: atualizar,
  })
  function abrir(n: Notificacao) {
    if (!n.lida && n.id) ler.mutate(n.id)
    if (n.link) navegar(n.link)
  }
  const naoLidas = (lista.data ?? []).filter((n) => !n.lida).length
  return (
    <div className="space-y-4 md:max-w-2xl">
      <TituloDaPagina subtitulo={<>Cobranças, reservas e avisos do condomínio. Escolha o que chega por e-mail e push em <Link to="/conta" className="text-marca-700 underline">Conta</Link>.</>}>
        Notificações
      </TituloDaPagina>
      {naoLidas > 0 && <Botao variante="secundario" carregando={todas.isPending} onClick={() => todas.mutate()}>Marcar todas como lidas</Botao>}
      {lista.isPending ? <Carregando /> : lista.isError ? <MensagemDeErro erro={lista.error} />
        : (lista.data ?? []).length === 0 ? <Vazio>Nenhuma notificação por enquanto.</Vazio> : (
          <ul className="space-y-2">
            {(lista.data ?? []).map((n) => (
              <li key={n.id}>
                <button onClick={() => abrir(n)}
                  className={`flex w-full gap-3 rounded-2xl p-4 text-left shadow-sm ring-1 ${n.lida ? 'bg-white ring-slate-200' : 'bg-marca-50 ring-marca-200'}`}>
                  <span aria-hidden className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-white text-sm font-semibold text-marca-700 ring-1 ring-slate-200">
                    {ICONE[n.grupo ?? 'DOCUMENTOS']}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="flex items-start justify-between gap-2">
                      <span className={`${n.lida ? 'font-medium' : 'font-semibold'} text-slate-900`}>{n.titulo}</span>
                      {!n.lida && <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-marca-600" aria-label="não lida" />}
                    </span>
                    <span className="block text-sm text-slate-600">{n.texto}</span>
                    <span className="mt-1 block text-xs text-slate-400">{dataHora(n.criadoEm)}</span>
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
    </div>
  )
}
