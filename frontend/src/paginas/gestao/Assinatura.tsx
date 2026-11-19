import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { api, dados } from '../../api/cliente'
import type { AssinaturaDoCondominio, FaturaDaAssinatura } from '../../api/tipos'
import { DadosParaPagar } from '../../componentes/Boleto'
import { Cartao, Carregando, Etiqueta, MensagemDeErro, TituloDaPagina, Vazio } from '../../componentes/ui'
import { useSessao } from '../../sessao/contexto'
import { data, moeda, periodoDaFatura, ROTULOS } from '../../util/formato'

const POR = { MENSAL: 'por mês', SEMESTRAL: 'por semestre', ANUAL: 'por ano' } as const

/**
 * A assinatura do Condomix, para o síndico e a administradora: o plano e as faturas, com Pix e boleto.
 * Com o condomínio suspenso por falta de pagamento, é a única tela que abre; pago, o acesso volta sozinho.
 */
export function Assinatura() {
  const { condominio, condominioId, escolherCondominio } = useSessao()
  const navegar = useNavigate()
  const pendente = !!condominio?.pagamentoPendente
  const assinatura = useQuery({
    queryKey: ['assinatura'],
    queryFn: async () => dados(await api.GET('/api/assinatura')),
    // Esperando a confirmação do pagamento, ou um boleto sendo gerado: confere de novo sozinho.
    refetchInterval: (q) => pendente || q.state.data?.faturas?.some((f) => f.situacao === 'A_EMITIR') ? 5000 : false,
  })
  const pagou = pendente && assinatura.data !== undefined && !assinatura.data.suspensaPorFaltaDePagamento
  useEffect(() => {
    // O pagamento foi confirmado: troca o acesso restrito pelo normal e volta ao início.
    if (pagou && condominioId) escolherCondominio(condominioId).then(() => navegar('/', { replace: true }))
  }, [pagou, condominioId, escolherCondominio, navegar])

  return (
    <div>
      <TituloDaPagina subtitulo="O plano do condomínio no Condomix e as faturas.">Assinatura do Condomix</TituloDaPagina>
      {assinatura.isPending ? <Carregando /> : assinatura.isError ? <MensagemDeErro erro={assinatura.error} /> : (
        <Conteudo a={assinatura.data} />
      )}
    </div>
  )
}

function Conteudo({ a }: { a: AssinaturaDoCondominio }) {
  const faturas = a.faturas ?? []
  const emAberto = faturas.filter((f) => f.situacao !== 'PAGA')
  const pagas = faturas.filter((f) => f.situacao === 'PAGA')
  return (
    <div className="space-y-6">
      {a.suspensaPorFaltaDePagamento && (
        <div role="alert" className="rounded-xl bg-red-50 p-4 text-sm text-red-900 ring-1 ring-red-200">
          <p className="font-semibold">O acesso ao Condomix está suspenso por falta de pagamento.</p>
          <p className="mt-1">
            Moradores e gestão não conseguem entrar até a fatura em atraso ser paga. Pague abaixo, com Pix ou boleto:
            assim que o pagamento for confirmado, o acesso volta sozinho para todos.
          </p>
        </div>
      )}
      <Cartao titulo="Plano">
        <dl className="space-y-1 text-sm">
          <div className="flex justify-between gap-2"><dt className="text-slate-600">Plano</dt><dd className="font-medium">{a.plano} · {ROTULOS.periodicidade[a.periodicidade ?? 'MENSAL']}</dd></div>
          <div className="flex justify-between gap-2"><dt className="text-slate-600">Valor</dt><dd>{a.preco != null ? `${moeda(a.preco)} ${POR[a.periodicidade ?? 'MENSAL']}` : 'A combinar'}</dd></div>
          <div className="flex justify-between gap-2">
            <dt className="text-slate-600">Situação</dt>
            <dd>{a.status === 'TESTE'
              ? <Etiqueta cor="marca">{a.fimDoTeste ? `Teste grátis até ${data(a.fimDoTeste)}` : 'Teste grátis'}</Etiqueta>
              : <Etiqueta cor={a.status === 'ATIVA' ? 'verde' : 'cinza'}>{ROTULOS.statusDaAssinatura[a.status ?? 'ATIVA']}</Etiqueta>}</dd>
          </div>
        </dl>
        {a.status === 'TESTE' && a.fimDoTeste && (
          <p className="mt-3 text-sm text-slate-600">
            Depois do teste, a assinatura passa a ser cobrada{a.preco != null && <>: {moeda(a.preco)} {POR[a.periodicidade ?? 'MENSAL']}</>}.
            A fatura chega aqui, com Pix e boleto.
          </p>
        )}
      </Cartao>
      <section>
        <h2 className="mb-2 text-sm font-semibold uppercase text-slate-500">Faturas em aberto ({emAberto.length})</h2>
        {emAberto.length === 0 ? <Vazio>Nenhuma fatura em aberto.</Vazio>
          : <ul className="grid gap-3 md:grid-cols-2">{emAberto.map((f) => <ItemDeFatura key={f.id} fatura={f} />)}</ul>}
      </section>
      {pagas.length > 0 && (
        <section>
          <h2 className="mb-2 text-sm font-semibold uppercase text-slate-500">Pagas</h2>
          <ul className="grid gap-3 md:grid-cols-2">{pagas.map((f) => <ItemDeFatura key={f.id} fatura={f} />)}</ul>
        </section>
      )}
    </div>
  )
}

function ItemDeFatura({ fatura: f }: { fatura: FaturaDaAssinatura }) {
  const [aberto, setAberto] = useState(f.atrasada === true)
  const pagavel = f.situacao === 'EMITIDA'
  const situacao = f.situacao === 'PAGA' ? <Etiqueta cor="verde">Paga em {data(f.pagoEm)}</Etiqueta>
    : f.atrasada ? <Etiqueta cor="vermelho">Venceu em {data(f.vencimento)}</Etiqueta>
    : f.situacao === 'EMITIDA' ? <Etiqueta cor="amarelo">Vence em {data(f.vencimento)}</Etiqueta>
    : <Etiqueta cor="cinza">{ROTULOS.situacaoDaFatura[f.situacao ?? 'A_EMITIR']}</Etiqueta>
  return (
    <li>
      <Cartao>
        <button className="w-full text-left" onClick={() => setAberto(!aberto)} aria-expanded={aberto} disabled={!pagavel}>
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <p className="font-medium">Assinatura Condomix</p>
              <p className="text-xs text-slate-500">{periodoDaFatura(f.periodoInicio, f.periodoFim)}</p>
            </div>
            <p className="shrink-0 text-right font-semibold">{moeda(f.valor)}</p>
          </div>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            {situacao}
            {pagavel && !aberto && <span className="text-sm font-medium text-marca-700">Pagar com Pix ou boleto ›</span>}
          </div>
        </button>
        {f.situacao === 'A_EMITIR' && <p className="mt-2 text-sm text-slate-600">O boleto está sendo gerado. Volte em instantes.</p>}
        {f.situacao === 'FALHOU' && <p className="mt-2 text-sm text-slate-600">O boleto ainda não está disponível. Fale com o suporte do Condomix.</p>}
        {aberto && pagavel && (
          <div className="mt-3 border-t border-slate-100 pt-3">
            <PagamentoDaFatura faturaId={f.id ?? ''} />
          </div>
        )}
      </Cartao>
    </li>
  )
}

function PagamentoDaFatura({ faturaId }: { faturaId: string }) {
  const via = useQuery({
    queryKey: ['assinatura', 'pagamento', faturaId],
    queryFn: async () => dados(await api.GET('/api/assinatura/faturas/{id}/pagamento', { params: { path: { id: faturaId } } })),
  })
  if (via.isPending) return <Carregando texto="Buscando o boleto…" />
  if (via.isError) return <MensagemDeErro erro={via.error} />
  return <DadosParaPagar v={via.data} />
}
