import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { api, dados } from '../api/cliente'
import type { Cobranca } from '../api/tipos'
import { Aviso, Cartao, Carregando, Etiqueta, MensagemDeErro, Vazio } from '../componentes/ui'
import { useSessao } from '../sessao/contexto'
import { ACOMPANHAMENTO } from '../sessao/papeis'
import { data, mesAtual, moeda, perfil, vinculo } from '../util/formato'

/** Resumo do dia: o que devo, minhas unidades, avisos da gestão, documentos novos e o caixa do mês. */
export function Inicio() {
  const { email, perfis, tem } = useSessao()
  const acompanha = tem(...ACOMPANHAMENTO)
  return (
    <div className="space-y-4">
      <div>
        <p className="text-sm text-slate-500">Olá,</p>
        <p className="truncate text-lg font-semibold">{email}</p>
        <div className="mt-1 flex flex-wrap gap-1">{perfis.map((p) => <Etiqueta key={p} cor="marca">{perfil(p)}</Etiqueta>)}</div>
      </div>
      {acompanha && <AvisosDeMandato />}
      <div className="grid gap-4 md:grid-cols-2">
        <ResumoDosDebitos />
        <ResumoDoCaixa />
        <MinhasUnidades />
        <DocumentosRecentes />
      </div>
    </div>
  )
}

function AvisosDeMandato() {
  const mandatos = useQuery({
    queryKey: ['mandatos', 'atuais'],
    queryFn: async () => dados(await api.GET('/api/mandatos', { params: { query: { somenteComAcesso: true } } })),
  })
  const avisos = (mandatos.data ?? []).filter((m) => m.aviso)
  if (avisos.length === 0) return null
  return (
    <div className="space-y-2">
      {avisos.map((m) => <Aviso key={m.id}><strong>{m.cargoRotulo} ({m.ocupante?.nome}):</strong> {m.aviso}</Aviso>)}
    </div>
  )
}

function ResumoDosDebitos() {
  const cobrancas = useQuery({ queryKey: ['cobrancas', 'minhas'], queryFn: async () => dados(await api.GET('/api/eu/cobrancas')) })
  const abertas: Cobranca[] = (cobrancas.data ?? []).filter((c) => c.status === 'ABERTA')
  const total = abertas.reduce((soma, c) => soma + (c.valorAtualizado ?? 0), 0)
  const vencidas = abertas.filter((c) => c.vencida).length
  const proxima = [...abertas].sort((a, b) => (a.vencimento ?? '').localeCompare(b.vencimento ?? ''))[0]
  return (
    <Cartao titulo="Meus débitos" acao={<Link to="/debitos" className="text-sm text-marca-700">Ver tudo</Link>}>
      {cobrancas.isPending ? <Carregando /> : cobrancas.isError ? <MensagemDeErro erro={cobrancas.error} /> : abertas.length === 0 ? (
        <p className="text-sm text-emerald-700">Nada em aberto. 👍</p>
      ) : (
        <div>
          <p className="text-2xl font-bold">{moeda(total)}</p>
          <p className="text-sm text-slate-500">
            {abertas.length} em aberto{vencidas > 0 && <span className="font-medium text-red-700"> · {vencidas} vencida(s)</span>}
            {proxima && ` · próximo vencimento ${data(proxima.vencimento)}`}
          </p>
        </div>
      )}
    </Cartao>
  )
}

function MinhasUnidades() {
  const unidades = useQuery({ queryKey: ['unidades', 'minhas'], queryFn: async () => dados(await api.GET('/api/eu/unidades')) })
  if (unidades.isPending || (unidades.data ?? []).length === 0) return null
  return (
    <Cartao titulo="Minhas unidades">
      <ul className="flex flex-wrap gap-2">
        {(unidades.data ?? []).map((u) => (
          <li key={u.vinculoId}><Etiqueta>{u.unidade} · {vinculo(u.vinculo)}</Etiqueta></li>
        ))}
      </ul>
    </Cartao>
  )
}

function DocumentosRecentes() {
  const documentos = useQuery({
    queryKey: ['documentos', 'recentes'],
    queryFn: async () => dados(await api.GET('/api/documentos', { params: { query: { tamanho: 3 } } })),
  })
  return (
    <Cartao titulo="Documentos recentes" acao={<Link to="/documentos" className="text-sm text-marca-700">Ver todos</Link>}>
      {documentos.isPending ? <Carregando /> : (documentos.data?.itens ?? []).length === 0 ? <Vazio>Nenhum documento publicado.</Vazio> : (
        <ul className="divide-y divide-slate-100">
          {(documentos.data?.itens ?? []).map((d) => (
            <li key={d.id} className="py-2">
              <p className="font-medium">{d.titulo}</p>
              <p className="text-xs text-slate-500">{d.categoriaRotulo}</p>
            </li>
          ))}
        </ul>
      )}
    </Cartao>
  )
}

function ResumoDoCaixa() {
  const mes = mesAtual()
  const balancete = useQuery({
    queryKey: ['balancete', mes],
    queryFn: async () => dados(await api.GET('/api/financeiro/balancete', { params: { query: { competencia: mes } } })),
  })
  return (
    <Cartao titulo="Caixa do condomínio" acao={<Link to="/balancete" className="text-sm text-marca-700">Balancete</Link>}>
      {balancete.isPending ? <Carregando /> : balancete.isError ? <MensagemDeErro erro={balancete.error} /> : (
        <dl className="grid grid-cols-2 gap-3 text-sm">
          <div><dt className="text-slate-500">Saldo</dt><dd className="text-lg font-semibold">{moeda(balancete.data?.saldoTotalFinal)}</dd></div>
          <div><dt className="text-slate-500">Resultado do mês</dt><dd className="text-lg font-semibold">{moeda(balancete.data?.resultado)}</dd></div>
        </dl>
      )}
    </Cartao>
  )
}
