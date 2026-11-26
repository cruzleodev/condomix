import { BotaoDeIcone } from '../../componentes/BotaoDeIcone'
import { CampoData } from '../../componentes/CampoData'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { api, dados } from '../../api/cliente'
import type { AlteracaoDaAssinatura, CondominioNaPlataforma } from '../../api/tipos'
import { Selecao } from '../../componentes/formulario'
import { Tabela } from '../../componentes/Tabela'
import { Botao, Campo, Cartao, Carregando, Etiqueta, MensagemDeErro, TituloDaPagina } from '../../componentes/ui'
import { data, dataHora, documento, opcoes, ROTULOS, telefone } from '../../util/formato'
import { Linha, SecaoDoSindico } from './SindicoDoCliente'
import { COR_DO_STATUS, descricaoDaAssinatura, usePlanosBase } from './comum'
import { GatewayDoCliente } from './GatewayDoCliente'
import { TabelaDeFaturas } from './TabelaDeFaturas'

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
        <SecaoDosDados cliente={c} />
      </div>
      <FaturasDoCliente condominioId={id} />
      <GatewayDoCliente condominioId={id} />
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
    <Cartao titulo="Assinatura" acao={<BotaoDeIcone icone="editar" rotulo="Alterar a assinatura" onClick={() => setEditando(true)} />}>
      {a ? (
        <dl className="space-y-1 text-sm">
          <Linha rotulo="Plano">{a.nomeDoPlano} · {ROTULOS.periodicidade[a.periodicidade ?? 'MENSAL']}</Linha>
          <Linha rotulo="Situação">{a.status && <Etiqueta cor={COR_DO_STATUS[a.status]}>{ROTULOS.statusDaAssinatura[a.status]}</Etiqueta>}</Linha>
          <Linha rotulo="Desde">{data(a.inicio)}</Linha>
          {a.status === 'TESTE' && <PrazoDoTeste cliente={cliente} />}
        </dl>
      ) : <p className="text-sm text-slate-600">Sem assinatura vigente.</p>}
    </Cartao>
  )
}

/** O fim do teste grátis: ao chegar, a assinatura fica ativa sozinha e a primeira fatura sai. */
function PrazoDoTeste({ cliente }: { cliente: CondominioNaPlataforma }) {
  const atualizar = useAtualizarCliente(cliente.id!)
  const atual = cliente.assinatura?.fimDoTeste ?? ''
  const [editando, setEditando] = useState(false)
  const [fim, setFim] = useState(atual)
  const salvar = useMutation({
    mutationFn: async (fimDoTeste: string | undefined) => dados(await api.PUT('/api/plataforma/condominios/{id}/fim-do-teste', {
      params: { path: { id: cliente.id! } }, body: { fimDoTeste },
    })),
    onSuccess: () => { atualizar(); setEditando(false) },
  })
  if (!editando) {
    return (
      <Linha rotulo="Teste grátis até">
        {atual ? data(atual) : 'sem prazo'}{' '}
        <span className="ml-1 inline-flex align-middle">
          <BotaoDeIcone icone="editar" rotulo="Mudar o fim do teste grátis" onClick={() => { setFim(atual); setEditando(true) }} />
        </span>
      </Linha>
    )
  }
  return (
    <form onSubmit={(e) => { e.preventDefault(); salvar.mutate(fim || undefined) }} className="space-y-2 pt-2">
      <CampoData rotulo="Fim do teste grátis" valor={fim} aoMudar={setFim}
        ajuda="Ao chegar, a assinatura fica ativa e a primeira fatura sai. Deixe vazio para um teste sem prazo." />
      <MensagemDeErro erro={salvar.error} />
      <div className="flex flex-col gap-2 md:flex-row">
        <Botao type="submit" carregando={salvar.isPending}>Salvar o prazo</Botao>
        <Botao type="button" variante="secundario" onClick={() => setEditando(false)}>Cancelar</Botao>
      </div>
    </form>
  )
}

/** CNPJ, e-mail e telefone do condomínio: o boleto da fatura sai no CNPJ. */
function SecaoDosDados({ cliente }: { cliente: CondominioNaPlataforma }) {
  const atualizar = useAtualizarCliente(cliente.id!)
  const [editando, setEditando] = useState(false)
  const [cnpj, setCnpj] = useState('')
  const [email, setEmail] = useState('')
  const [fone, setFone] = useState('')
  const salvar = useMutation({
    mutationFn: async () => dados(await api.PUT('/api/plataforma/condominios/{id}/dados', {
      params: { path: { id: cliente.id! } }, body: { cnpj: cnpj || undefined, email: email || undefined, telefone: fone || undefined },
    })),
    onSuccess: () => { atualizar(); setEditando(false) },
  })
  function editar() {
    setCnpj(cliente.cnpj ? documento(cliente.cnpj) : '')
    setEmail(cliente.email ?? '')
    setFone(cliente.telefone ? telefone(cliente.telefone) : '')
    setEditando(true)
  }
  if (editando) {
    return (
      <Cartao titulo="Dados para a fatura">
        <form onSubmit={(e) => { e.preventDefault(); salvar.mutate() }} className="space-y-3">
          <Campo rotulo="CNPJ do condomínio" value={cnpj} onChange={(e) => setCnpj(e.target.value)} ajuda="O banco exige no boleto da fatura." />
          <div className="grid gap-3 md:grid-cols-2">
            <Campo rotulo="E-mail" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
            <Campo rotulo="Telefone" type="tel" value={fone} onChange={(e) => setFone(e.target.value)} />
          </div>
          <MensagemDeErro erro={salvar.error} />
          <div className="flex flex-col gap-2 md:flex-row">
            <Botao type="submit" carregando={salvar.isPending}>Salvar</Botao>
            <Botao type="button" variante="secundario" onClick={() => setEditando(false)}>Cancelar</Botao>
          </div>
        </form>
      </Cartao>
    )
  }
  return (
    <Cartao titulo="Dados para a fatura" acao={<BotaoDeIcone icone="editar" rotulo="Alterar os dados para a fatura" onClick={editar} />}>
      <dl className="space-y-1 text-sm">
        <Linha rotulo="CNPJ">{cliente.cnpj ? documento(cliente.cnpj) : <span className="text-red-700">Falta: o boleto não sai sem ele</span>}</Linha>
        <Linha rotulo="E-mail">{cliente.email || '—'}</Linha>
        <Linha rotulo="Telefone">{cliente.telefone ? telefone(cliente.telefone) : '—'}</Linha>
      </dl>
    </Cartao>
  )
}

function FaturasDoCliente({ condominioId }: { condominioId: string }) {
  const faturas = useQuery({
    queryKey: ['plataforma', 'faturas', 'cliente', condominioId],
    queryFn: async () => dados(await api.GET('/api/plataforma/faturas', { params: { query: { condominioId } } })),
    refetchInterval: (q) => q.state.data?.some((f) => f.situacao === 'A_EMITIR' || f.situacao === 'A_CANCELAR') ? 5000 : false,
  })
  return (
    <section>
      <h2 className="mb-2 text-sm font-semibold uppercase text-slate-500">Faturas da assinatura</h2>
      {faturas.isPending ? <Carregando /> : faturas.isError ? <MensagemDeErro erro={faturas.error} />
        : <TabelaDeFaturas faturas={faturas.data} mostrarCliente={false} />}
    </section>
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
        {cliente.ativo ? <Etiqueta cor="verde">Liberado</Etiqueta>
          : <Etiqueta cor="vermelho">{ROTULOS.motivoDaSuspensao[cliente.motivoSuspensao ?? 'MANUAL']}</Etiqueta>}
        <span className="ml-2 text-slate-600">
          {cliente.ativo ? 'Moradores e gestão entram normalmente.'
            : cliente.motivoSuspensao === 'FALTA_DE_PAGAMENTO'
              ? 'Só a gestão entra, para pagar a fatura. Paga a fatura, o acesso volta sozinho.'
              : 'Ninguém do condomínio consegue entrar.'}
        </span>
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
