import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { api, dados } from '../api/cliente'
import { CartaoDeReserva } from '../componentes/Reservas'
import { Carregando, MensagemDeErro, TituloDaPagina, Vazio } from '../componentes/ui'
import { horario, moeda } from '../util/formato'
import { resumoDasRegras } from '../util/reservas'

/** Os espaços comuns para reservar e as reservas das minhas unidades. */
export function Reservas() {
  const [aviso, setAviso] = useState<string | null>(null)
  const espacos = useQuery({ queryKey: ['reservas', 'espacos'], queryFn: async () => dados(await api.GET('/api/espacos')) })
  const minhas = useQuery({ queryKey: ['reservas', 'minhas'], queryFn: async () => dados(await api.GET('/api/eu/reservas')) })
  const lista = minhas.data ?? []
  const proximas = lista.filter((r) => r.situacao === 'CONFIRMADA' && !r.aconteceu)
  const anteriores = lista.filter((r) => r.situacao !== 'CONFIRMADA' || r.aconteceu)
  const ativos = (espacos.data ?? []).filter((e) => e.ativo)
  return (
    <div className="space-y-6">
      <TituloDaPagina subtitulo="Salão de festas, churrasqueira e outros espaços comuns.">Reservas</TituloDaPagina>
      <section>
        <h2 className="mb-2 text-sm font-semibold uppercase text-slate-500">Espaços</h2>
        {espacos.isPending ? <Carregando /> : espacos.isError ? <MensagemDeErro erro={espacos.error} />
          : ativos.length === 0 ? <Vazio>O condomínio ainda não cadastrou espaços para reservar.</Vazio> : (
            <ul className="grid gap-3 md:grid-cols-2">
              {ativos.map((e) => (
                <li key={e.id}>
                  <Link to={`/reservas/${e.id}`} className="block h-full rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200 hover:ring-marca-600">
                    <div className="flex items-start justify-between gap-3">
                      <p className="font-semibold text-slate-900">{e.nome}</p>
                      <p className="shrink-0 text-sm font-medium">{(e.valorAluguel ?? 0) > 0 ? moeda(e.valorAluguel) : 'Sem aluguel'}</p>
                    </div>
                    {e.descricao && <p className="text-sm text-slate-600">{e.descricao}</p>}
                    <p className="mt-1 text-xs text-slate-500">
                      {(e.horarios ?? []).map((h) => horario(h.nome, h.inicio, h.fim)).join(' · ')}
                      {e.capacidade && ` · até ${e.capacidade} pessoas`}
                    </p>
                    <ul className="mt-2 list-disc space-y-0.5 pl-5 text-xs text-slate-500">
                      {resumoDasRegras(e).map((f) => <li key={f}>{f}</li>)}
                    </ul>
                    <p className="mt-2 text-sm font-medium text-marca-700">Ver a agenda e reservar ›</p>
                  </Link>
                </li>
              ))}
            </ul>
          )}
      </section>
      <section>
        <h2 className="mb-2 text-sm font-semibold uppercase text-slate-500">Minhas reservas ({proximas.length})</h2>
        {aviso && <p role="status" className="mb-3 rounded-xl bg-emerald-50 p-3 text-sm text-emerald-900 ring-1 ring-emerald-200">{aviso}</p>}
        {minhas.isPending ? <Carregando /> : minhas.isError ? <MensagemDeErro erro={minhas.error} />
          : proximas.length === 0 ? <Vazio>Nenhuma reserva marcada.</Vazio>
          : <ul className="grid gap-3 md:grid-cols-2">{proximas.map((r) => <CartaoDeReserva key={r.id} reserva={r} aoCancelar={setAviso} />)}</ul>}
      </section>
      {anteriores.length > 0 && (
        <section>
          <h2 className="mb-2 text-sm font-semibold uppercase text-slate-500">Anteriores e canceladas</h2>
          <ul className="grid gap-3 md:grid-cols-2">{anteriores.map((r) => <CartaoDeReserva key={r.id} reserva={r} />)}</ul>
        </section>
      )}
    </div>
  )
}
