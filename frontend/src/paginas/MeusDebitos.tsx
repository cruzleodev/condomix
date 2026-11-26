import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { api, dados } from '../api/cliente'
import type { Cobranca } from '../api/tipos'
import { SegundaVia } from '../componentes/Boleto'
import { atualizarEnquantoProcessa } from '../util/boleto'
import { Cartao, Carregando, Etiqueta, MensagemDeErro, TituloDaPagina, Vazio } from '../componentes/ui'
import { competencia, data, moeda } from '../util/formato'

/** As cobranças das unidades em que sou proprietário ou inquilino: em aberto (com multa e juros até hoje) e pagas. */
export function MeusDebitos() {
  const cobrancas = useQuery({
    queryKey: ['cobrancas', 'minhas'],
    queryFn: async () => dados(await api.GET('/api/eu/cobrancas')),
    refetchInterval: (q) => atualizarEnquantoProcessa(q.state.data),
  })
  const lista = cobrancas.data ?? []
  const abertas = lista.filter((c) => c.status === 'ABERTA')
  const pagas = lista.filter((c) => c.status === 'PAGA')
  return (
    <div>
      <TituloDaPagina subtitulo="Valores em aberto já com multa e juros até hoje.">Meus débitos</TituloDaPagina>
      {cobrancas.isPending ? <Carregando /> : cobrancas.isError ? <MensagemDeErro erro={cobrancas.error} /> : lista.length === 0 ? (
        <Vazio>Nenhuma cobrança nas suas unidades.</Vazio>
      ) : (
        <div className="space-y-6">
          <section>
            <h2 className="mb-2 text-sm font-semibold uppercase text-slate-500">Em aberto ({abertas.length})</h2>
            {abertas.length === 0 ? <Vazio>Nada em aberto. 👍</Vazio> : <ul className="grid gap-3 md:grid-cols-2">{abertas.map((c) => <ItemDeCobranca key={c.id} cobranca={c} />)}</ul>}
          </section>
          {pagas.length > 0 && (
            <section>
              <h2 className="mb-2 text-sm font-semibold uppercase text-slate-500">Pagas nos últimos 12 meses</h2>
              <ul className="grid gap-3 md:grid-cols-2">{pagas.map((c) => <ItemDeCobranca key={c.id} cobranca={c} />)}</ul>
            </section>
          )}
        </div>
      )}
    </div>
  )
}

function ItemDeCobranca({ cobranca: c }: { cobranca: Cobranca }) {
  const [aberto, setAberto] = useState(false)
  const pagavel = c.status === 'ABERTA' && c.boleto?.situacao === 'EMITIDO'
  const situacao = c.status === 'PAGA'
    ? <Etiqueta cor="verde">Paga em {data(c.pagoEm)}</Etiqueta>
    : c.vencida ? <Etiqueta cor="vermelho">Vencida há {c.diasDeAtraso} dia(s)</Etiqueta>
    : <Etiqueta cor="amarelo">Vence em {data(c.vencimento)}</Etiqueta>
  return (
    <li>
      <Cartao>
        <button className="w-full text-left" onClick={() => setAberto(!aberto)} aria-expanded={aberto}>
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <p className="line-clamp-2 font-medium">{c.descricao}</p>
              <p className="text-xs text-slate-500">Unidade {c.unidade}{c.competencia && ` · ${competencia(c.competencia)}`}</p>
            </div>
            <p className="shrink-0 text-right font-semibold">{moeda(c.status === 'PAGA' ? c.valorPago : c.valorAtualizado)}</p>
          </div>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            {situacao}
            {pagavel && !aberto && <span className="text-sm font-medium text-marca-700">Pagar com Pix ou boleto ›</span>}
          </div>
        </button>
        {aberto && (
          <dl className="mt-3 space-y-1 border-t border-slate-100 pt-3 text-sm">
            {(c.itens ?? []).map((item, i) => (
              <div key={i} className="flex justify-between gap-2"><dt className="text-slate-600">{item.descricao}</dt><dd>{moeda(item.valor)}</dd></div>
            ))}
            {(c.multa ?? 0) > 0 && <div className="flex justify-between"><dt className="text-slate-600">Multa</dt><dd>{moeda(c.multa)}</dd></div>}
            {(c.juros ?? 0) > 0 && <div className="flex justify-between"><dt className="text-slate-600">Juros</dt><dd>{moeda(c.juros)}</dd></div>}
          </dl>
        )}
        {aberto && c.status === 'ABERTA' && (
          <div className="mt-3 border-t border-slate-100 pt-3">
            {pagavel ? <SegundaVia cobrancaId={c.id ?? ''} dono="eu" />
              : c.boleto?.situacao === 'PENDENTE' ? <p className="text-sm text-slate-600">O boleto está sendo gerado. Volte em instantes.</p>
              : c.boleto?.situacao === 'FALHOU' ? <p className="text-sm text-slate-600">O boleto ainda não está disponível. Fale com o síndico.</p>
              : null}
          </div>
        )}
      </Cartao>
    </li>
  )
}
