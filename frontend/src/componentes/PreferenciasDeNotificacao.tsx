import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { api, dados } from '../api/cliente'
import type { PreferenciaDoGrupo } from '../api/tipos'
import { useSessao } from '../sessao/contexto'
import { GESTAO } from '../sessao/papeis'
import { desinscrever, inscrever, situacaoDoPush, type SituacaoDoPush } from '../util/push'
import { Aviso, Botao, Cartao, Carregando, MensagemDeErro } from './ui'

const TEXTO_DO_PUSH: Record<SituacaoDoPush, string> = {
  'sem-suporte': 'Este navegador não recebe notificações push. No iPhone, instale o app na tela inicial (Compartilhar → Adicionar à Tela de Início).',
  'sem-app': 'O push funciona com o app instalado ou aberto no endereço publicado (não no ambiente de desenvolvimento).',
  bloqueado: 'As notificações estão bloqueadas para o Condomix neste navegador. Libere nas configurações do site e volte aqui.',
  desligado: 'O push ainda não está ligado neste aparelho.',
  ligado: 'Este aparelho recebe as notificações push.',
}

/** Na Conta: o que chega por e-mail e push, e o botão para ligar o push neste aparelho. No app (o sino), sempre. */
export function PreferenciasDeNotificacao() {
  const cache = useQueryClient()
  const { tem } = useSessao()
  const preferencias = useQuery({
    queryKey: ['notificacoes', 'preferencias'],
    queryFn: async () => dados(await api.GET('/api/notificacoes/preferencias')),
  })
  const salvar = useMutation({
    mutationFn: async (grupos: PreferenciaDoGrupo[]) => dados(await api.PUT('/api/notificacoes/preferencias', {
      body: grupos.map((g) => ({ grupo: g.grupo ?? 'COBRANCAS', email: !!g.email, push: !!g.push })),
    })),
    onSuccess: (resposta) => cache.setQueryData(['notificacoes', 'preferencias'], resposta),
  })
  if (preferencias.isPending) return <Cartao titulo="Notificações"><Carregando /></Cartao>
  if (preferencias.isError) return <Cartao titulo="Notificações"><MensagemDeErro erro={preferencias.error} /></Cartao>
  const p = preferencias.data
  // A assinatura do Condomix só interessa à gestão.
  const grupos = (p.grupos ?? []).filter((g) => g.grupo !== 'ASSINATURA' || tem(...GESTAO))
  function mudar(grupo: PreferenciaDoGrupo, canal: 'email' | 'push', valor: boolean) {
    salvar.mutate((p.grupos ?? []).map((g) => (g.grupo === grupo.grupo ? { ...g, [canal]: valor } : g)))
  }
  return (
    <Cartao titulo="Notificações">
      <p className="mb-3 text-sm text-slate-600">No app (o sino) você sempre recebe. Escolha o que também chega por e-mail e por push.</p>
      {!p.emailLigado && <div className="mb-3"><Aviso>O envio de e-mail ainda não está ligado no Condomix.</Aviso></div>}
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-xs uppercase text-slate-500">
            <th className="pb-2 font-semibold">Assunto</th>
            <th className="w-16 pb-2 text-center font-semibold">E-mail</th>
            <th className="w-16 pb-2 text-center font-semibold">Push</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {grupos.map((g) => (
            <tr key={g.grupo}>
              <td className="py-2 pr-2">
                <span className="block font-medium">{g.rotulo}</span>
                <span className="block text-xs text-slate-500">{g.exemplos}</span>
              </td>
              <td className="text-center">
                <input type="checkbox" aria-label={`${g.rotulo} por e-mail`} className="h-5 w-5 accent-marca-700"
                  checked={!!g.email} disabled={salvar.isPending} onChange={(e) => mudar(g, 'email', e.target.checked)} />
              </td>
              <td className="text-center">
                <input type="checkbox" aria-label={`${g.rotulo} por push`} className="h-5 w-5 accent-marca-700"
                  checked={!!g.push} disabled={salvar.isPending} onChange={(e) => mudar(g, 'push', e.target.checked)} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <MensagemDeErro erro={salvar.error} />
      <PushNesteAparelho />
    </Cartao>
  )
}

function PushNesteAparelho() {
  const cache = useQueryClient()
  const [situacao, setSituacao] = useState<SituacaoDoPush | null>(null)
  useEffect(() => {
    situacaoDoPush().then(setSituacao)
  }, [])
  const alternar = useMutation({
    mutationFn: async () => {
      if (situacao === 'ligado') {
        const endpoint = await desinscrever()
        if (endpoint) dados(await api.POST('/api/notificacoes/push/cancelamento', { body: { endpoint } }))
      } else {
        const chave = dados(await api.GET('/api/notificacoes/push/chave'))
        dados(await api.POST('/api/notificacoes/push/inscricao', { body: await inscrever(chave.chavePublica ?? '') }))
      }
    },
    onSuccess: async () => {
      setSituacao(await situacaoDoPush())
      cache.invalidateQueries({ queryKey: ['notificacoes', 'preferencias'] })
    },
  })
  if (situacao === null) return null
  return (
    <div className="mt-4 border-t border-slate-100 pt-3 text-sm">
      <p className="mb-2 text-slate-600">{TEXTO_DO_PUSH[situacao]}</p>
      {(situacao === 'desligado' || situacao === 'ligado') && (
        <Botao variante="secundario" className="w-full md:w-auto" carregando={alternar.isPending} onClick={() => alternar.mutate()}>
          {situacao === 'ligado' ? 'Desligar o push neste aparelho' : 'Ligar o push neste aparelho'}
        </Botao>
      )}
      <MensagemDeErro erro={alternar.error} />
    </div>
  )
}
