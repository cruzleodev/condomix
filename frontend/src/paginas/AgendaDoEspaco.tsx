import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { api, dados } from '../api/cliente'
import type { DiaNaAgenda, Espaco, HorarioNaAgenda } from '../api/tipos'
import { Selecao } from '../componentes/formulario'
import { Aviso, Botao, Campo, Cartao, Carregando, MensagemDeErro, TituloDaPagina, Vazio } from '../componentes/ui'
import { useSessao } from '../sessao/contexto'
import { GESTAO } from '../sessao/papeis'
import { competencia, data, diaDaSemana, hojeIso, horario, mesAtual, moeda, periodoDoMes, proximoMes, somarDias } from '../util/formato'
import { resumoDasRegras } from '../util/reservas'

interface Escolha {
  dia: string
  horario: HorarioNaAgenda
}

/** A agenda de um espaço, mês a mês: os horários livres viram botão de reservar. */
export function AgendaDoEspaco() {
  const { id = '' } = useParams()
  const [mes, setMes] = useState(mesAtual())
  const [escolha, setEscolha] = useState<Escolha | null>(null)
  const [reservado, setReservado] = useState<string | null>(null)
  const espaco = useQuery({
    queryKey: ['reservas', 'espaco', id],
    queryFn: async () => dados(await api.GET('/api/espacos/{id}', { params: { path: { id } } })),
  })
  const [de, ate] = periodoDoMes(mes)
  const agenda = useQuery({
    queryKey: ['reservas', 'agenda', id, mes],
    queryFn: async () => dados(await api.GET('/api/espacos/{id}/agenda', { params: { path: { id }, query: { de, ate } } })),
  })
  if (espaco.isPending) return <Carregando />
  if (espaco.isError) return <MensagemDeErro erro={espaco.error} />
  const e = espaco.data
  const dias = (agenda.data ?? []).filter((d) => (d.horarios ?? []).some((h) => h.situacao !== 'INDISPONIVEL'))
  const anterior = mesAnterior(mes)
  return (
    <div className="space-y-4">
      <Link to="/reservas" className="text-sm text-marca-700 underline">← Reservas</Link>
      <TituloDaPagina subtitulo={(e.valorAluguel ?? 0) > 0 ? `Aluguel de ${moeda(e.valorAluguel)} por reserva` : 'Sem aluguel'}>{e.nome}</TituloDaPagina>
      <ul className="list-disc space-y-0.5 pl-5 text-sm text-slate-600">{resumoDasRegras(e).map((f) => <li key={f}>{f}</li>)}</ul>
      {reservado && <p role="status" className="rounded-xl bg-emerald-50 p-3 text-sm text-emerald-900 ring-1 ring-emerald-200">{reservado}</p>}
      {escolha && (
        <ConfirmacaoDaReserva espaco={e} escolha={escolha} aoCancelar={() => setEscolha(null)}
          aoReservar={(texto) => { setEscolha(null); setReservado(texto) }} />
      )}
      <div className="flex items-center justify-between gap-2">
        <Botao variante="secundario" disabled={anterior < mesAtual()} onClick={() => setMes(anterior)} aria-label="Mês anterior">‹</Botao>
        <p className="font-semibold capitalize">{competencia(mes)}</p>
        <Botao variante="secundario" onClick={() => setMes(proximoMes(mes))} aria-label="Próximo mês">›</Botao>
      </div>
      {agenda.isPending ? <Carregando /> : agenda.isError ? <MensagemDeErro erro={agenda.error} />
        : dias.length === 0 ? <Vazio>Nenhum horário para reservar neste mês.</Vazio> : (
          <ul className="grid gap-3 md:grid-cols-2">
            {dias.map((d) => <DiaDaAgenda key={d.data} dia={d} aoEscolher={(h) => { setReservado(null); setEscolha({ dia: d.data ?? '', horario: h }) }} />)}
          </ul>
        )}
    </div>
  )
}

function mesAnterior(anoMes: string): string {
  const [ano, mes] = anoMes.split('-').map(Number)
  return mes === 1 ? `${ano - 1}-12` : `${ano}-${String(mes - 1).padStart(2, '0')}`
}

function DiaDaAgenda({ dia, aoEscolher }: { dia: DiaNaAgenda; aoEscolher: (h: HorarioNaAgenda) => void }) {
  return (
    <li className="rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
      <p className="mb-2 font-medium capitalize">{diaDaSemana(dia.data)}</p>
      <ul className="flex flex-wrap gap-2">
        {(dia.horarios ?? []).map((h) => (
          <li key={h.nome}>
            {h.situacao === 'LIVRE' ? (
              <button onClick={() => aoEscolher(h)}
                className="min-h-11 rounded-xl border border-marca-700 px-3 text-sm font-medium text-marca-700 hover:bg-marca-50">
                {horario(h.nome, h.inicio, h.fim)}
              </button>
            ) : (
              <span className={`inline-flex min-h-11 items-center rounded-xl px-3 text-sm ${h.situacao === 'DA_MINHA_UNIDADE' ? 'bg-emerald-50 text-emerald-800' : 'bg-slate-100 text-slate-500'}`}>
                {horario(h.nome, h.inicio, h.fim)} · {h.situacao === 'DA_MINHA_UNIDADE' ? 'sua reserva'
                  : h.situacao === 'OCUPADO' ? `reservado${h.unidade ? ` (${h.unidade})` : ''}` : 'indisponível'}
              </span>
            )}
          </li>
        ))}
      </ul>
    </li>
  )
}

function ConfirmacaoDaReserva({ espaco: e, escolha, aoCancelar, aoReservar }: {
  espaco: Espaco; escolha: Escolha; aoCancelar: () => void; aoReservar: (texto: string) => void
}) {
  const cache = useQueryClient()
  const { tem } = useSessao()
  const unidades = useQuery({ queryKey: ['reservas', 'unidades'], queryFn: async () => dados(await api.GET('/api/reservas/unidades')) })
  const [unidade, setUnidade] = useState('')
  const [observacao, setObservacao] = useState('')
  const lista = unidades.data ?? []
  const escolhida = unidade || lista[0]?.id || ''
  const reservar = useMutation({
    mutationFn: async () => dados(await api.POST('/api/reservas', {
      body: { espacoId: e.id ?? '', data: escolha.dia, horario: escolha.horario.nome ?? '', unidadeId: escolhida, observacao: observacao || undefined },
    })),
    onSuccess: (r) => {
      cache.invalidateQueries({ queryKey: ['reservas'] })
      cache.invalidateQueries({ queryKey: ['cobrancas'] })
      aoReservar(`Reservado: ${e.nome}, ${diaDaSemana(r.data)}, ${horario(r.horario, r.inicio, r.fim)}.`
        + ((r.valor ?? 0) > 0 ? ` O aluguel de ${moeda(r.valor)} já está em Meus débitos.` : ''))
    },
  })
  const aluguel = (e.valorAluguel ?? 0) > 0
  const dias = e.regras?.diasParaCancelar ?? 0
  return (
    <Cartao titulo="Confirmar a reserva">
      <form className="space-y-3" onSubmit={(ev) => { ev.preventDefault(); reservar.mutate() }}>
        <p className="text-sm">
          <strong>{e.nome}</strong>, {diaDaSemana(escolha.dia)}, {horario(escolha.horario.nome, escolha.horario.inicio, escolha.horario.fim)}.
        </p>
        {unidades.isPending ? <Carregando texto="Buscando suas unidades…" />
          : lista.length === 0 ? <Aviso>{tem(...GESTAO) ? 'Não há unidades ativas.' : 'Você não está ligado a nenhuma unidade. Fale com o síndico.'}</Aviso>
          : lista.length > 1 ? (
            <Selecao rotulo="Para a unidade" value={escolhida} onChange={(ev) => setUnidade(ev.target.value)}
              opcoes={lista.map((u) => ({ valor: u.id ?? '', rotulo: u.descricao ?? '' }))} />
          ) : <p className="text-sm text-slate-600">Para a unidade {lista[0].descricao}.</p>}
        <Campo rotulo="Observação (opcional)" value={observacao} onChange={(ev) => setObservacao(ev.target.value)} maxLength={500}
          ajuda="Ex.: aniversário, cerca de 30 pessoas." />
        {aluguel && (
          <p className="text-sm text-slate-700">
            O aluguel de <strong>{moeda(e.valorAluguel)}</strong> vira uma cobrança da unidade, vencendo no dia da reserva.
            {' '}{somarDias(escolha.dia, -dias) >= hojeIso()
              ? `Cancelando até ${data(somarDias(escolha.dia, -dias))}, ela é cancelada.`
              : `Faltam menos de ${dias} dia(s): se cancelar, o aluguel continua devido.`}
          </p>
        )}
        {escolha.dia < hojeIso() && <Aviso>Esta data já passou.</Aviso>}
        <MensagemDeErro erro={reservar.error} />
        <div className="flex flex-col gap-2 md:flex-row">
          <Botao type="submit" carregando={reservar.isPending} disabled={!escolhida}>Confirmar a reserva</Botao>
          <Botao type="button" variante="secundario" onClick={aoCancelar}>Escolher outro horário</Botao>
        </div>
      </form>
    </Cartao>
  )
}
