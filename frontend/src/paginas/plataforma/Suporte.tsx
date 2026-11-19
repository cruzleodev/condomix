import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { api, dados } from '../../api/cliente'
import type { RegistroDeSuporte } from '../../api/tipos'
import { AreaDeTexto, Selecao } from '../../componentes/formulario'
import { Tabela } from '../../componentes/Tabela'
import { Aviso, Botao, Cartao, Carregando, Etiqueta, MensagemDeErro, TituloDaPagina } from '../../componentes/ui'
import { useSessao } from '../../sessao/contexto'
import { dataHora } from '../../util/formato'

/**
 * Modo suporte: o administrador entra num condomínio só para ler, por tempo limitado e com motivo.
 * Cada acesso fica registrado na auditoria abaixo.
 */
export function Suporte() {
  const navegar = useNavigate()
  const cache = useQueryClient()
  const { entrarComoSuporte } = useSessao()
  const [parametros] = useSearchParams()
  const [condominioId, setCondominioId] = useState(parametros.get('condominio') ?? '')
  const [motivo, setMotivo] = useState('')

  const clientes = useQuery({ queryKey: ['plataforma', 'condominios'], queryFn: async () => dados(await api.GET('/api/plataforma/condominios')) })
  const registros = useQuery({ queryKey: ['plataforma', 'suporte'], queryFn: async () => dados(await api.GET('/api/plataforma/suporte')) })
  const nomeDe = (id?: string) => clientes.data?.find((c) => c.id === id)?.nome ?? '—'

  const abrir = useMutation({
    mutationFn: async () => dados(await api.POST('/api/plataforma/suporte', { body: { condominioId, motivo } })),
    onSuccess: (acesso) => {
      cache.invalidateQueries({ queryKey: ['plataforma', 'suporte'] })
      cache.clear()
      entrarComoSuporte(acesso.token!, { id: condominioId, nome: nomeDe(condominioId) })
      navegar('/', { replace: true })
    },
  })

  function enviar(e: FormEvent) {
    e.preventDefault()
    abrir.mutate()
  }

  return (
    <div className="space-y-4">
      <TituloDaPagina subtitulo="Para atender um cliente: você vê o condomínio como a gestão vê, sem poder alterar nada.">Suporte</TituloDaPagina>
      <Cartao titulo="Entrar num condomínio">
        <form onSubmit={enviar} className="space-y-3">
          <Selecao rotulo="Condomínio" required vazio="Escolha…" value={condominioId} onChange={(e) => setCondominioId(e.target.value)}
            opcoes={(clientes.data ?? []).map((c) => ({ valor: c.id ?? '', rotulo: c.nome ?? '' }))} />
          <AreaDeTexto rotulo="Motivo" required minLength={10} maxLength={500} value={motivo} onChange={(e) => setMotivo(e.target.value)}
            ajuda="Obrigatório (mínimo de 10 letras). Fica registrado na auditoria abaixo." />
          <Aviso>O acesso é <strong>somente leitura</strong> e vence sozinho em 1 hora. "Voltar ao painel", na faixa amarela, encerra antes.</Aviso>
          <MensagemDeErro erro={abrir.error} />
          <Botao type="submit" carregando={abrir.isPending} disabled={!condominioId || motivo.trim().length < 10} className="w-full md:w-auto">
            Entrar no condomínio (somente leitura)
          </Botao>
        </form>
      </Cartao>

      <section>
        <h2 className="mb-2 text-sm font-semibold uppercase text-slate-500">Acessos registrados</h2>
        {registros.isPending ? <Carregando /> : registros.isError ? <MensagemDeErro erro={registros.error} /> : (
          <Tabela<RegistroDeSuporte>
            linhas={registros.data ?? []}
            chave={(r) => r.id ?? ''}
            vazio="Nenhum acesso de suporte até agora."
            colunas={[
              { titulo: 'Condomínio', valor: (r) => <span className="font-medium">{nomeDe(r.condominioId)}</span> },
              { titulo: 'Quem', valor: (r) => r.administradorEmail },
              { titulo: 'Início', valor: (r) => dataHora(r.inicio) },
              { titulo: 'Situação', valor: (r) => <SituacaoDoAcesso registro={r} /> },
              { titulo: 'Motivo', valor: (r) => <span className="whitespace-pre-line">{r.motivo}</span> },
            ]}
          />
        )}
      </section>
    </div>
  )
}

/** Em andamento (com o botão para encerrar), encerrado antes do prazo ou vencido. */
function SituacaoDoAcesso({ registro }: { registro: RegistroDeSuporte }) {
  const cache = useQueryClient()
  const [agora] = useState(() => Date.now())
  const encerrar = useMutation({
    mutationFn: async () => dados(await api.POST('/api/plataforma/suporte/{id}/encerramento', { params: { path: { id: registro.id! } } })),
    onSuccess: () => cache.invalidateQueries({ queryKey: ['plataforma', 'suporte'] }),
    onError: (e) => window.alert(e.message),
  })
  if (registro.encerradoEm) return <span>Encerrado em {dataHora(registro.encerradoEm)}</span>
  if (registro.expiraEm && new Date(registro.expiraEm).getTime() <= agora) return <span>Venceu em {dataHora(registro.expiraEm)}</span>
  return (
    <span className="flex flex-wrap items-center gap-2">
      <Etiqueta cor="amarelo">Em andamento até {dataHora(registro.expiraEm)}</Etiqueta>
      <button className="text-sm text-red-700 underline" disabled={encerrar.isPending} onClick={() => encerrar.mutate()}>Encerrar</button>
    </span>
  )
}
