import { BotaoDeIcone } from '../../componentes/BotaoDeIcone'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { api, dados } from '../../api/cliente'
import type { LoginNaPlataforma } from '../../api/tipos'
import { AreaDeTexto } from '../../componentes/formulario'
import { Tabela } from '../../componentes/Tabela'
import { Aviso, Botao, Campo, Cartao, Carregando, Etiqueta, MensagemDeErro, TituloDaPagina } from '../../componentes/ui'
import { useSessao } from '../../sessao/contexto'
import { dataHora, perfil } from '../../util/formato'

/**
 * Logins: busca por e-mail e bloqueio (ex.: suspeita de invasão da conta). Mostra só dados de acesso,
 * nunca os dados internos dos condomínios.
 */
export function Logins() {
  const [termo, setTermo] = useState('')
  const [busca, setBusca] = useState('')
  const [bloqueando, setBloqueando] = useState<LoginNaPlataforma | null>(null)
  const logins = useQuery({
    queryKey: ['plataforma', 'logins', busca],
    queryFn: async () => dados(await api.GET('/api/plataforma/logins', { params: { query: { email: busca } } })),
    enabled: busca.length >= 3,
  })

  function buscar(e: FormEvent) {
    e.preventDefault()
    setBloqueando(null)
    setBusca(termo.trim())
  }

  return (
    <div className="space-y-4">
      <TituloDaPagina subtitulo="Para bloquear o acesso de alguém em todos os condomínios, por exemplo numa suspeita de invasão da conta.">
        Logins
      </TituloDaPagina>
      <form onSubmit={buscar} className="flex flex-col gap-2 md:flex-row md:items-end">
        <div className="md:w-96">
          <Campo rotulo="E-mail (ou parte dele)" type="search" minLength={3} required value={termo} onChange={(e) => setTermo(e.target.value)} />
        </div>
        <Botao type="submit">Buscar</Botao>
      </form>

      {bloqueando && <Bloqueio login={bloqueando} aoTerminar={() => setBloqueando(null)} />}

      {busca.length < 3 ? null : logins.isPending ? <Carregando /> : logins.isError ? <MensagemDeErro erro={logins.error} /> : (
        <Tabela<LoginNaPlataforma>
          linhas={logins.data}
          chave={(l) => l.id ?? ''}
          vazio="Nenhum login com esse e-mail."
          colunas={[
            { titulo: 'E-mail', valor: (l) => (
              <span>
                <span className="font-medium">{l.email}</span>
                {l.administradorDaPlataforma && <span className="block text-xs text-slate-500">Administrador do Condomix</span>}
              </span>
            ) },
            { titulo: 'Condomínios', valor: (l) => (l.condominios ?? []).length === 0 ? '—' : (
              <ul className="space-y-0.5">
                {(l.condominios ?? []).map((c) => (
                  <li key={c.condominioId}>{c.nome} <span className="text-slate-500">· {(c.perfis ?? []).map(perfil).join(', ')}</span></li>
                ))}
              </ul>
            ) },
            { titulo: 'Último acesso', valor: (l) => dataHora(l.ultimoAcesso) },
            { titulo: 'Situação', valor: (l) => l.bloqueado ? (
              <span>
                <Etiqueta cor="vermelho">Bloqueado</Etiqueta>
                <span className="mt-1 block text-xs text-slate-500">{dataHora(l.bloqueadoEm)} · {l.motivoDoBloqueio}</span>
              </span>
            ) : <Etiqueta cor="verde">Liberado</Etiqueta> },
            { titulo: 'Ações', direita: true, valor: (l) => <AcaoDoLogin login={l} aoBloquear={() => setBloqueando(l)} /> },
          ]}
        />
      )}
    </div>
  )
}

function AcaoDoLogin({ login, aoBloquear }: { login: LoginNaPlataforma; aoBloquear: () => void }) {
  const { email } = useSessao()
  const cache = useQueryClient()
  const desbloquear = useMutation({
    mutationFn: async () => dados(await api.POST('/api/plataforma/logins/{id}/desbloqueio', { params: { path: { id: login.id! } } })),
    onSuccess: () => cache.invalidateQueries({ queryKey: ['plataforma', 'logins'] }),
    onError: (e) => window.alert(e.message),
  })
  if (login.email === email) return <span className="text-sm text-slate-400">Você</span>
  if (login.bloqueado) {
    return (
      <BotaoDeIcone icone="desbloquear" rotulo="Desbloquear o login" disabled={desbloquear.isPending} onClick={() => desbloquear.mutate()} />
    )
  }
  return <BotaoDeIcone icone="bloquear" rotulo="Bloquear o login" perigo onClick={aoBloquear} />
}

function Bloqueio({ login, aoTerminar }: { login: LoginNaPlataforma; aoTerminar: () => void }) {
  const cache = useQueryClient()
  const [motivo, setMotivo] = useState('')
  const bloquear = useMutation({
    mutationFn: async () => dados(await api.POST('/api/plataforma/logins/{id}/bloqueio', {
      params: { path: { id: login.id! } }, body: { motivo },
    })),
    onSuccess: () => {
      cache.invalidateQueries({ queryKey: ['plataforma', 'logins'] })
      aoTerminar()
    },
  })
  const quantos = (login.condominios ?? []).length
  return (
    <Cartao titulo={`Bloquear ${login.email}`}>
      <form onSubmit={(e) => { e.preventDefault(); bloquear.mutate() }} className="space-y-3">
        <Aviso>
          O acesso cai na hora, inclusive em quem já está com o app aberto
          {quantos > 1 ? `, nos ${quantos} condomínios deste login` : ''}. Para liberar de novo, use "Desbloquear".
        </Aviso>
        <AreaDeTexto rotulo="Motivo" required minLength={10} maxLength={500} value={motivo} onChange={(e) => setMotivo(e.target.value)}
          ajuda="Obrigatório (mínimo de 10 letras). Aparece na lista de logins." />
        <MensagemDeErro erro={bloquear.error} />
        <div className="flex flex-col gap-2 md:flex-row">
          <Botao type="submit" variante="perigo" carregando={bloquear.isPending} disabled={motivo.trim().length < 10}>Bloquear login</Botao>
          <Botao type="button" variante="secundario" onClick={aoTerminar}>Cancelar</Botao>
        </div>
      </form>
    </Cartao>
  )
}
