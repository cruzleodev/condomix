import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { api, dados } from '../api/cliente'
import { Cartao, Carregando, MensagemDeErro, TituloDaPagina, Vazio } from '../componentes/ui'
import { competencia, mesAtual, moeda } from '../util/formato'

/** Prestação de contas do mês, aberta a todos do condomínio. */
export function Balancete() {
  const [mes, setMes] = useState(mesAtual())
  const balancete = useQuery({
    queryKey: ['balancete', mes],
    queryFn: async () => dados(await api.GET('/api/financeiro/balancete', { params: { query: { competencia: mes } } })),
  })
  const b = balancete.data
  return (
    <div>
      <TituloDaPagina subtitulo="Prestação de contas do condomínio.">Balancete</TituloDaPagina>
      <label className="mb-4 block">
        <span className="mb-1 block text-sm font-medium text-slate-700">Mês</span>
        <input type="month" value={mes} max={mesAtual()} onChange={(e) => e.target.value && setMes(e.target.value)}
          className="min-h-11 rounded-xl border border-slate-300 bg-white px-3" />
      </label>
      {balancete.isPending ? <Carregando /> : balancete.isError ? <MensagemDeErro erro={balancete.error} /> : b && (
        <div className="space-y-4">
          <Cartao titulo={`Resumo de ${competencia(b.competencia)}`}>
            <dl className="grid grid-cols-2 gap-3 text-sm">
              <Valor rotulo="Receitas" valor={b.totalReceitas} cor="text-emerald-700" />
              <Valor rotulo="Despesas" valor={b.totalDespesas} cor="text-red-700" />
              <Valor rotulo="Resultado do mês" valor={b.resultado} />
              <Valor rotulo="Saldo em caixa" valor={b.saldoTotalFinal} />
              <Valor rotulo="Inadimplência" valor={b.inadimplencia} cor="text-amber-700" />
            </dl>
          </Cartao>
          <Lista titulo="Receitas por categoria" itens={b.receitas ?? []} />
          <Lista titulo="Despesas por categoria" itens={b.despesas ?? []} />
          <Cartao titulo="Contas">
            {(b.contas ?? []).length === 0 ? <Vazio>Nenhuma conta cadastrada.</Vazio> : (
              <ul className="divide-y divide-slate-100 text-sm">
                {(b.contas ?? []).map((c) => (
                  <li key={c.conta} className="py-2">
                    <p className="font-medium">{c.conta}</p>
                    <p className="text-slate-500">
                      Anterior {moeda(c.saldoAnterior)} · entradas {moeda(c.entradas)} · saídas {moeda(c.saidas)}
                    </p>
                    <p>Saldo final <strong>{moeda(c.saldoFinal)}</strong></p>
                  </li>
                ))}
              </ul>
            )}
          </Cartao>
        </div>
      )}
    </div>
  )
}

function Valor({ rotulo, valor, cor = '' }: { rotulo: string; valor?: number; cor?: string }) {
  return <div><dt className="text-slate-500">{rotulo}</dt><dd className={`text-lg font-semibold ${cor}`}>{moeda(valor)}</dd></div>
}

function Lista({ titulo, itens }: { titulo: string; itens: { categoria?: string; total?: number }[] }) {
  return (
    <Cartao titulo={titulo}>
      {itens.length === 0 ? <Vazio>Nenhum lançamento.</Vazio> : (
        <ul className="divide-y divide-slate-100 text-sm">
          {itens.map((i) => (
            <li key={i.categoria} className="flex justify-between py-2"><span>{i.categoria}</span><span className="font-medium">{moeda(i.total)}</span></li>
          ))}
        </ul>
      )}
    </Cartao>
  )
}
