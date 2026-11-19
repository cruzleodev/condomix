import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { api, dados } from '../../api/cliente'
import type { ClienteNovo, CondominioNaPlataforma, PedidoClienteNovo } from '../../api/tipos'
import { LinkDeConvite, Selecao } from '../../componentes/formulario'
import { Tabela } from '../../componentes/Tabela'
import { Botao, Campo, Cartao, Carregando, Etiqueta, MensagemDeErro, TituloDaPagina } from '../../componentes/ui'
import { data, documento, hojeIso, opcoes, ROTULOS, umAnoDepoisMenosUmDia } from '../../util/formato'

type Periodicidade = PedidoClienteNovo['periodicidade']
type Status = NonNullable<NonNullable<CondominioNaPlataforma['assinatura']>['status']>

const COR_DO_STATUS = { TESTE: 'marca', ATIVA: 'verde', SUSPENSA: 'vermelho', CANCELADA: 'cinza' } as const

function usePlanosBase() {
  const planos = useQuery({ queryKey: ['plataforma', 'planos'], queryFn: async () => dados(await api.GET('/api/plataforma/planos')) })
  return (planos.data ?? []).filter((p) => p.tipo === 'BASE' && p.ativo)
}

/** Painel do Condomix: os condomínios clientes, a assinatura de cada um e o cadastro de cliente novo. */
export function Clientes() {
  const navegar = useNavigate()
  const [novo, setNovo] = useState(false)
  const [editando, setEditando] = useState<CondominioNaPlataforma | null>(null)
  const clientes = useQuery({ queryKey: ['plataforma', 'condominios'], queryFn: async () => dados(await api.GET('/api/plataforma/condominios')) })
  const lista = clientes.data ?? []
  const ativos = lista.filter((c) => c.ativo).length

  return (
    <div className="space-y-4">
      <TituloDaPagina subtitulo={clientes.data ? `${lista.length} condomínios · ${ativos} ativos` : undefined}>Clientes</TituloDaPagina>
      {!novo && !editando && <Botao onClick={() => setNovo(true)}>Novo cliente</Botao>}
      {novo && <CadastroDeCliente aoTerminar={() => setNovo(false)} />}
      {editando && <EdicaoDeAssinatura cliente={editando} aoTerminar={() => setEditando(null)} />}

      {clientes.isPending ? <Carregando /> : clientes.isError ? <MensagemDeErro erro={clientes.error} /> : (
        <Tabela<CondominioNaPlataforma>
          linhas={lista}
          chave={(c) => c.id ?? ''}
          vazio="Nenhum cliente ainda."
          colunas={[
            { titulo: 'Condomínio', valor: (c) => (
              <span>
                <span className="font-medium">{c.nome}</span>
                <span className="block text-xs text-slate-500">{ROTULOS.tipoDeCondominio[c.tipo ?? 'MISTO']}{c.cnpj ? ` · ${documento(c.cnpj)}` : ''}</span>
              </span>
            ) },
            { titulo: 'Plano', valor: (c) => c.assinatura
              ? `${c.assinatura.nomeDoPlano} · ${ROTULOS.periodicidade[c.assinatura.periodicidade ?? 'MENSAL']}` : '—' },
            { titulo: 'Assinatura', valor: (c) => c.assinatura?.status
              ? <Etiqueta cor={COR_DO_STATUS[c.assinatura.status]}>{ROTULOS.statusDaAssinatura[c.assinatura.status]}</Etiqueta> : '—' },
            { titulo: 'Cliente desde', valor: (c) => data(c.criadoEm) },
            { titulo: 'Acesso', valor: (c) => c.ativo ? <Etiqueta cor="verde">Liberado</Etiqueta> : <Etiqueta cor="vermelho">Suspenso</Etiqueta> },
            { titulo: 'Ações', direita: true, valor: (c) => (
              <span className="flex flex-wrap justify-end gap-3 text-sm">
                <button className="text-marca-700 underline" onClick={() => { setNovo(false); setEditando(c) }}>Assinatura</button>
                <SuspenderOuReativar cliente={c} />
                <button className="text-marca-700 underline" onClick={() => navegar(`/plataforma/suporte?condominio=${c.id}`)}>Suporte</button>
              </span>
            ) },
          ]}
        />
      )}
    </div>
  )
}

function SuspenderOuReativar({ cliente }: { cliente: CondominioNaPlataforma }) {
  const cache = useQueryClient()
  const acao = useMutation({
    mutationFn: async () => {
      const caminho = { params: { path: { id: cliente.id! } } }
      dados(cliente.ativo
        ? await api.POST('/api/plataforma/condominios/{id}/suspensao', caminho)
        : await api.POST('/api/plataforma/condominios/{id}/reativacao', caminho))
    },
    onSuccess: () => cache.invalidateQueries({ queryKey: ['plataforma', 'condominios'] }),
    onError: (e) => window.alert(e.message),
  })
  function clicar() {
    if (cliente.ativo && !window.confirm(`Suspender ${cliente.nome}? Ninguém do condomínio conseguirá entrar até a reativação.`)) return
    acao.mutate()
  }
  return (
    <button className={cliente.ativo ? 'text-red-700 underline' : 'text-emerald-700 underline'} disabled={acao.isPending} onClick={clicar}>
      {acao.isPending ? 'Aguarde…' : cliente.ativo ? 'Suspender' : 'Reativar'}
    </button>
  )
}

function CadastroDeCliente({ aoTerminar }: { aoTerminar: () => void }) {
  const cache = useQueryClient()
  const planos = usePlanosBase()
  const hoje = hojeIso()
  const [criado, setCriado] = useState<ClienteNovo | null>(null)
  const [f, setF] = useState({
    nome: '', tipo: 'RESIDENCIAL_VERTICAL' as PedidoClienteNovo['tipo'], cnpj: '', plano: '', periodicidade: 'MENSAL' as Periodicidade,
    sindicoNome: '', sindicoEmail: '', sindicoDocumento: '', inicio: hoje, fim: umAnoDepoisMenosUmDia(hoje),
  })
  const muda = (campo: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [campo]: e.target.value })
  const plano = f.plano || planos[0]?.codigo || ''

  const cadastrar = useMutation({
    mutationFn: async () => dados(await api.POST('/api/plataforma/condominios', { body: {
      nome: f.nome, tipo: f.tipo, cnpj: f.cnpj || undefined, plano, periodicidade: f.periodicidade,
      sindico: { nome: f.sindicoNome, email: f.sindicoEmail, documento: f.sindicoDocumento || undefined, inicioMandato: f.inicio, fimMandato: f.fim },
    } })),
    onSuccess: (resultado) => {
      setCriado(resultado)
      cache.invalidateQueries({ queryKey: ['plataforma', 'condominios'] })
    },
  })

  if (criado) {
    return (
      <Cartao titulo={`${criado.condominio?.nome} cadastrado`}>
        <p className="mb-3 text-sm text-slate-600">
          O condomínio começa em teste. O síndico cria a senha pelo link abaixo e já entra como síndico.
        </p>
        {criado.conviteDoSindico?.link && <LinkDeConvite link={criado.conviteDoSindico.link} email={criado.conviteDoSindico.email} />}
        <Botao variante="secundario" className="mt-3 w-full md:w-auto" onClick={aoTerminar}>Fechar</Botao>
      </Cartao>
    )
  }

  function enviar(e: FormEvent) {
    e.preventDefault()
    cadastrar.mutate()
  }

  return (
    <Cartao titulo="Novo cliente">
      <form onSubmit={enviar} className="space-y-4">
        <fieldset className="grid gap-3 md:grid-cols-2">
          <legend className="mb-2 text-sm font-semibold text-slate-700">Condomínio</legend>
          <Campo rotulo="Nome" required maxLength={150} value={f.nome} onChange={muda('nome')} />
          <Selecao rotulo="Tipo" opcoes={opcoes(ROTULOS.tipoDeCondominio)} value={f.tipo} onChange={muda('tipo')} />
          <Campo rotulo="CNPJ (opcional)" value={f.cnpj} onChange={muda('cnpj')} ajuda="Aceita o CNPJ com letras." />
          <Selecao rotulo="Plano" opcoes={planos.map((p) => ({ valor: p.codigo ?? '', rotulo: p.nome ?? '' }))} value={plano} onChange={muda('plano')} />
          <Selecao rotulo="Periodicidade" opcoes={opcoes(ROTULOS.periodicidade)} value={f.periodicidade} onChange={muda('periodicidade')} />
        </fieldset>
        <fieldset className="grid gap-3 md:grid-cols-2">
          <legend className="mb-2 text-sm font-semibold text-slate-700">Síndico (recebe o convite)</legend>
          <Campo rotulo="Nome" required maxLength={150} value={f.sindicoNome} onChange={muda('sindicoNome')} />
          <Campo rotulo="E-mail" type="email" required value={f.sindicoEmail} onChange={muda('sindicoEmail')} />
          <Campo rotulo="CPF ou CNPJ (opcional)" value={f.sindicoDocumento} onChange={muda('sindicoDocumento')} />
          <div className="grid grid-cols-2 gap-3">
            <Campo rotulo="Início do mandato" type="date" required value={f.inicio}
              onChange={(e) => setF({ ...f, inicio: e.target.value, fim: e.target.value ? umAnoDepoisMenosUmDia(e.target.value) : f.fim })} />
            <Campo rotulo="Fim do mandato" type="date" required value={f.fim} onChange={muda('fim')} />
          </div>
        </fieldset>
        <MensagemDeErro erro={cadastrar.error} />
        <div className="flex flex-col gap-2 md:flex-row">
          <Botao type="submit" carregando={cadastrar.isPending} disabled={!plano}>Cadastrar e gerar convite</Botao>
          <Botao type="button" variante="secundario" onClick={aoTerminar}>Cancelar</Botao>
        </div>
      </form>
    </Cartao>
  )
}

function EdicaoDeAssinatura({ cliente, aoTerminar }: { cliente: CondominioNaPlataforma; aoTerminar: () => void }) {
  const cache = useQueryClient()
  const planos = usePlanosBase()
  const [plano, setPlano] = useState(cliente.assinatura?.plano ?? '')
  const [periodicidade, setPeriodicidade] = useState<Periodicidade>(cliente.assinatura?.periodicidade ?? 'MENSAL')
  const [status, setStatus] = useState<Status>(cliente.assinatura?.status ?? 'TESTE')
  const salvar = useMutation({
    mutationFn: async () => dados(await api.PUT('/api/plataforma/condominios/{id}/assinatura', {
      params: { path: { id: cliente.id! } }, body: { plano, periodicidade, status },
    })),
    onSuccess: () => {
      cache.invalidateQueries({ queryKey: ['plataforma', 'condominios'] })
      aoTerminar()
    },
  })
  const { CANCELADA: _cancelada, ...statusPermitidos } = ROTULOS.statusDaAssinatura
  return (
    <Cartao titulo={`Assinatura de ${cliente.nome}`}>
      <form onSubmit={(e) => { e.preventDefault(); salvar.mutate() }} className="space-y-3">
        <div className="grid gap-3 md:grid-cols-3">
          <Selecao rotulo="Plano" opcoes={planos.map((p) => ({ valor: p.codigo ?? '', rotulo: p.nome ?? '' }))} value={plano} onChange={(e) => setPlano(e.target.value)} />
          <Selecao rotulo="Periodicidade" opcoes={opcoes(ROTULOS.periodicidade)} value={periodicidade} onChange={(e) => setPeriodicidade(e.target.value as Periodicidade)} />
          <Selecao rotulo="Situação" opcoes={opcoes(statusPermitidos)} value={status} onChange={(e) => setStatus(e.target.value as Status)} />
        </div>
        <p className="text-xs text-slate-500">Trocar o plano ou a periodicidade guarda a assinatura anterior no histórico.</p>
        <MensagemDeErro erro={salvar.error} />
        <div className="flex flex-col gap-2 md:flex-row">
          <Botao type="submit" carregando={salvar.isPending}>Salvar</Botao>
          <Botao type="button" variante="secundario" onClick={aoTerminar}>Cancelar</Botao>
        </div>
      </form>
    </Cartao>
  )
}
