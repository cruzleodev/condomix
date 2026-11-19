import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { api, dados } from '../../api/cliente'
import type { AlteracaoDaAssinatura, CondominioNaPlataforma } from '../../api/tipos'
import { Selecao } from '../../componentes/formulario'
import { Tabela } from '../../componentes/Tabela'
import { Botao, Cartao, Carregando, Etiqueta, MensagemDeErro, TituloDaPagina } from '../../componentes/ui'
import { data, dataHora, documento, opcoes, ROTULOS } from '../../util/formato'
import { Linha, SecaoDoSindico } from './SindicoDoCliente'
import { COR_DO_STATUS, descricaoDaAssinatura, usePlanosBase } from './comum'
import { GatewayDoCliente } from './GatewayDoCliente'

type Periodicidade = NonNullable<NonNullable<CondominioNaPlataforma['assinatura']>['periodicidade']>
type Status = NonNullable<NonNullable<CondominioNaPlataforma['assinatura']>['status']>

const chaves = {
  cliente: (id: string) => ['plataforma', 'condominio', id],
  historico: (id: string) => ['plataforma', 'historico', id],
}

/** Um cliente: assinatura, síndico (convite e troca), acesso ao app e o histórico de tudo o que mudou. */
export function Cliente() {
  const { id = '' } = useParams()
  const cliente = useQuery({
    queryKey: chaves.cliente(id),
    queryFn: async () => dados(await api.GET('/api/plataforma/condominios/{id}', { params: { path: { id } } })),
  })

  if (cliente.isPending) return <Carregando />
  if (cliente.isError) return <MensagemDeErro erro={cliente.error} />
  const c = cliente.data
  return (
    <div className="space-y-4">
      <Link to="/plataforma" className="text-sm text-marca-700 underline">← Clientes</Link>
      <TituloDaPagina subtitulo={`${ROTULOS.tipoDeCondominio[c.tipo ?? 'MISTO']}${c.cnpj ? ` · ${documento(c.cnpj)}` : ''} · cliente desde ${data(c.criadoEm)}`}>
        {c.nome}
      </TituloDaPagina>
      <div className="grid gap-4 md:grid-cols-2">
        <SecaoDaAssinatura cliente={c} />
        <SecaoDoAcesso cliente={c} />
      </div>
      <div className="grid gap-4 md:grid-cols-2">
        <SecaoDoSindico condominioId={id} />
        <GatewayDoCliente condominioId={id} />
      </div>
      <Historico condominioId={id} />
    </div>
  )
}

function useAtualizarCliente(id: string) {
  const cache = useQueryClient()
  return () => {
    cache.invalidateQueries({ queryKey: chaves.cliente(id) })
    cache.invalidateQueries({ queryKey: chaves.historico(id) })
    cache.invalidateQueries({ queryKey: ['plataforma', 'condominios'] })
  }
}

function SecaoDaAssinatura({ cliente }: { cliente: CondominioNaPlataforma }) {
  const [editando, setEditando] = useState(false)
  const a = cliente.assinatura
  if (editando) return <EdicaoDeAssinatura cliente={cliente} aoTerminar={() => setEditando(false)} />
  return (
    <Cartao titulo="Assinatura" acao={<button className="text-sm text-marca-700 underline" onClick={() => setEditando(true)}>Alterar</button>}>
      {a ? (
        <dl className="space-y-1 text-sm">
          <Linha rotulo="Plano">{a.nomeDoPlano} · {ROTULOS.periodicidade[a.periodicidade ?? 'MENSAL']}</Linha>
          <Linha rotulo="Situação">{a.status && <Etiqueta cor={COR_DO_STATUS[a.status]}>{ROTULOS.statusDaAssinatura[a.status]}</Etiqueta>}</Linha>
          <Linha rotulo="Desde">{data(a.inicio)}</Linha>
        </dl>
      ) : <p className="text-sm text-slate-600">Sem assinatura vigente.</p>}
    </Cartao>
  )
}

function EdicaoDeAssinatura({ cliente, aoTerminar }: { cliente: CondominioNaPlataforma; aoTerminar: () => void }) {
  const atualizar = useAtualizarCliente(cliente.id!)
  const planos = usePlanosBase()
  const [plano, setPlano] = useState(cliente.assinatura?.plano ?? '')
  const [periodicidade, setPeriodicidade] = useState<Periodicidade>(cliente.assinatura?.periodicidade ?? 'MENSAL')
  const [status, setStatus] = useState<Status>(cliente.assinatura?.status ?? 'TESTE')
  const salvar = useMutation({
    mutationFn: async () => dados(await api.PUT('/api/plataforma/condominios/{id}/assinatura', {
      params: { path: { id: cliente.id! } }, body: { plano: plano || planos[0]?.codigo || '', periodicidade, status },
    })),
    onSuccess: () => {
      atualizar()
      aoTerminar()
    },
  })
  const { CANCELADA: _cancelada, ...statusPermitidos } = ROTULOS.statusDaAssinatura
  return (
    <Cartao titulo="Alterar a assinatura">
      <form onSubmit={(e) => { e.preventDefault(); salvar.mutate() }} className="space-y-3">
        <Selecao rotulo="Plano" opcoes={planos.map((p) => ({ valor: p.codigo ?? '', rotulo: p.nome ?? '' }))} value={plano} onChange={(e) => setPlano(e.target.value)} />
        <div className="grid grid-cols-2 gap-3">
          <Selecao rotulo="Periodicidade" opcoes={opcoes(ROTULOS.periodicidade)} value={periodicidade} onChange={(e) => setPeriodicidade(e.target.value as Periodicidade)} />
          <Selecao rotulo="Situação" opcoes={opcoes(statusPermitidos)} value={status} onChange={(e) => setStatus(e.target.value as Status)} />
        </div>
        <p className="text-xs text-slate-500">A mudança fica no histórico, com a data e o seu e-mail.</p>
        <MensagemDeErro erro={salvar.error} />
        <div className="flex flex-col gap-2 md:flex-row">
          <Botao type="submit" carregando={salvar.isPending}>Salvar</Botao>
          <Botao type="button" variante="secundario" onClick={aoTerminar}>Cancelar</Botao>
        </div>
      </form>
    </Cartao>
  )
}

function SecaoDoAcesso({ cliente }: { cliente: CondominioNaPlataforma }) {
  const atualizar = useAtualizarCliente(cliente.id!)
  const acao = useMutation({
    mutationFn: async () => {
      const caminho = { params: { path: { id: cliente.id! } } }
      dados(cliente.ativo
        ? await api.POST('/api/plataforma/condominios/{id}/suspensao', caminho)
        : await api.POST('/api/plataforma/condominios/{id}/reativacao', caminho))
    },
    onSuccess: atualizar,
  })
  function clicar() {
    if (cliente.ativo && !window.confirm(`Suspender ${cliente.nome}? Ninguém do condomínio conseguirá entrar até a reativação.`)) return
    acao.mutate()
  }
  return (
    <Cartao titulo="Acesso ao app">
      <p className="mb-3 text-sm">
        {cliente.ativo ? <Etiqueta cor="verde">Liberado</Etiqueta> : <Etiqueta cor="vermelho">Suspenso</Etiqueta>}
        <span className="ml-2 text-slate-600">{cliente.ativo ? 'Moradores e gestão entram normalmente.' : 'Ninguém do condomínio consegue entrar.'}</span>
      </p>
      <MensagemDeErro erro={acao.error} />
      <div className="flex flex-col gap-2 md:flex-row">
        <Botao variante={cliente.ativo ? 'perigo' : 'principal'} carregando={acao.isPending} onClick={clicar}>
          {cliente.ativo ? 'Suspender o condomínio' : 'Reativar o condomínio'}
        </Botao>
        <Link to={`/plataforma/suporte?condominio=${cliente.id}`}
          className="rounded-lg border border-slate-300 px-4 py-2 text-center text-sm font-medium text-slate-700 hover:bg-slate-50">
          Entrar como suporte
        </Link>
      </div>
    </Cartao>
  )
}

function Historico({ condominioId }: { condominioId: string }) {
  const historico = useQuery({
    queryKey: chaves.historico(condominioId),
    queryFn: async () => dados(await api.GET('/api/plataforma/condominios/{id}/historico', { params: { path: { id: condominioId } } })),
  })
  return (
    <section>
      <h2 className="mb-2 text-sm font-semibold uppercase text-slate-500">Histórico da assinatura</h2>
      {historico.isPending ? <Carregando /> : historico.isError ? <MensagemDeErro erro={historico.error} /> : (
        <Tabela<AlteracaoDaAssinatura>
          linhas={historico.data}
          chave={(h) => `${h.momento}-${h.tipo}`}
          vazio="Nenhuma mudança registrada."
          colunas={[
            { titulo: 'Quando', valor: (h) => dataHora(h.momento) },
            { titulo: 'O que', valor: (h) => <span className="font-medium">{h.tipo ? ROTULOS.tipoDeEventoDaAssinatura[h.tipo] : ''}</span> },
            { titulo: 'Assinatura', valor: (h) => descricaoDaAssinatura(h) },
            { titulo: 'Quem', valor: (h) => h.feitoPor ?? 'Sistema' },
          ]}
        />
      )}
    </section>
  )
}
