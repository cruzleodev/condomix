import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { api, dados } from '../../api/cliente'
import type { ClienteNovo, CondominioNaPlataforma, PedidoClienteNovo } from '../../api/tipos'
import { LinkDeConvite, Selecao } from '../../componentes/formulario'
import { Tabela } from '../../componentes/Tabela'
import { Botao, Campo, Cartao, Carregando, Etiqueta, MensagemDeErro, TituloDaPagina } from '../../componentes/ui'
import { data, documento, hojeIso, opcoes, ROTULOS, umAnoDepoisMenosUmDia } from '../../util/formato'
import { COR_DO_STATUS, usePlanosBase } from './comum'

type Periodicidade = PedidoClienteNovo['periodicidade']


/** Painel do Condomix: os condomínios clientes, a assinatura de cada um e o cadastro de cliente novo. */
export function Clientes() {
  const navegar = useNavigate()
  const [novo, setNovo] = useState(false)
  const clientes = useQuery({ queryKey: ['plataforma', 'condominios'], queryFn: async () => dados(await api.GET('/api/plataforma/condominios')) })
  const lista = clientes.data ?? []
  const ativos = lista.filter((c) => c.ativo).length

  return (
    <div className="space-y-4">
      <TituloDaPagina subtitulo={clientes.data ? `${lista.length} condomínios · ${ativos} ativos` : undefined}>Clientes</TituloDaPagina>
      {!novo && <Botao onClick={() => setNovo(true)}>Novo cliente</Botao>}
      {novo && <CadastroDeCliente aoTerminar={() => setNovo(false)} />}

      {clientes.isPending ? <Carregando /> : clientes.isError ? <MensagemDeErro erro={clientes.error} /> : (
        <Tabela<CondominioNaPlataforma>
          linhas={lista}
          chave={(c) => c.id ?? ''}
          vazio="Nenhum cliente ainda."
          colunas={[
            { titulo: 'Condomínio', valor: (c) => (
              <span>
                <Link to={`/plataforma/clientes/${c.id}`} className="font-medium text-marca-700 underline">{c.nome}</Link>
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
                <Link className="text-marca-700 underline" to={`/plataforma/clientes/${c.id}`}>Abrir</Link>
                <button className="text-marca-700 underline" onClick={() => navegar(`/plataforma/suporte?condominio=${c.id}`)}>Suporte</button>
              </span>
            ) },
          ]}
        />
      )}
    </div>
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
          Se o link se perder, gere outro na página do cliente.
        </p>
        {criado.conviteDoSindico?.link
          ? <LinkDeConvite link={criado.conviteDoSindico.link} email={criado.conviteDoSindico.email} />
          : <p className="text-sm text-slate-600">O síndico já tinha login no Condomix: entra com a senha dele.</p>}
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
