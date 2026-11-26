import { BotaoDeIcone } from './BotaoDeIcone'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api, dados } from '../api/cliente'
import type { Reserva } from '../api/tipos'
import { data, diaDaSemana, hojeIso, horario, moeda } from '../util/formato'
import { MENSAGEM_DO_CANCELAMENTO } from '../util/reservas'
import { Botao, Campo, Etiqueta, MensagemDeErro } from './ui'

/** A situação da reserva numa etiqueta: confirmada, realizada, cancelada. */
export function EtiquetaDaReserva({ reserva: r }: { reserva: Reserva }) {
  if (r.situacao === 'CANCELADA') return <Etiqueta cor="cinza">Cancelada{r.aluguelMantido ? ' (aluguel devido)' : ''}</Etiqueta>
  if (r.aconteceu) return <Etiqueta cor="cinza">Realizada</Etiqueta>
  return <Etiqueta cor="verde">Confirmada</Etiqueta>
}

/** Uma reserva em cartão, com o botão de cancelar (até o dia). */
export function CartaoDeReserva({ reserva: r, gestao = false, aoCancelar }:
  { reserva: Reserva; gestao?: boolean; aoCancelar?: (mensagem: string) => void }) {
  const cancelavel = r.situacao === 'CONFIRMADA' && !r.aconteceu
  return (
    <li className="rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="font-medium">{r.espaco}</p>
          <p className="text-sm text-slate-600">{diaDaSemana(r.data)} · {horario(r.horario, r.inicio, r.fim)}</p>
          <p className="text-xs text-slate-500">Unidade {r.unidade}{(r.valor ?? 0) > 0 && ` · aluguel ${moeda(r.valor)}`}</p>
        </div>
        <EtiquetaDaReserva reserva={r} />
      </div>
      {r.observacao && <p className="mt-2 text-sm text-slate-600">“{r.observacao}”</p>}
      {r.situacao === 'CANCELADA' && r.motivoCancelamento && <p className="mt-2 text-xs text-slate-500">Motivo: {r.motivoCancelamento}</p>}
      {cancelavel && <CancelamentoDeReserva reserva={r} gestao={gestao} aoCancelar={aoCancelar} />}
    </li>
  )
}

/**
 * Cancelar: avisa se o aluguel será cancelado (no prazo) ou continua devido. Cancelada, a reserva muda de lista;
 * por isso o resultado também vai para {@code aoCancelar}, que mostra a mensagem fora do cartão.
 */
export function CancelamentoDeReserva({ reserva: r, gestao, aoCancelar, abertoDeInicio = false }:
  { reserva: Reserva; gestao: boolean; aoCancelar?: (mensagem: string) => void; abertoDeInicio?: boolean }) {
  const cache = useQueryClient()
  const [aberto, setAberto] = useState(abertoDeInicio)
  const [motivo, setMotivo] = useState('')
  const [dispensar, setDispensar] = useState(false)
  const cancelar = useMutation({
    mutationFn: async () => dados(await api.POST('/api/reservas/{id}/cancelamento', {
      params: { path: { id: r.id ?? '' } }, body: { motivo: motivo || undefined, dispensarAluguel: gestao && dispensar },
    })),
    onSuccess: (resultado) => {
      aoCancelar?.(MENSAGEM_DO_CANCELAMENTO[resultado.aluguel ?? 'SEM_ALUGUEL'])
      cache.invalidateQueries({ queryKey: ['reservas'] })
      cache.invalidateQueries({ queryKey: ['cobrancas'] })
    },
  })
  if (cancelar.data) {
    return <p role="status" className="mt-3 text-sm text-slate-700">{MENSAGEM_DO_CANCELAMENTO[cancelar.data.aluguel ?? 'SEM_ALUGUEL']}</p>
  }
  const temAluguel = (r.valor ?? 0) > 0 && !!r.cobrancaId
  const noPrazo = hojeIso() <= (r.cancelavelSemCustoAte ?? '')
  if (!aberto) {
    return <div className="mt-2"><BotaoDeIcone icone="cancelar" rotulo="Cancelar a reserva" perigo dica="direita" onClick={() => setAberto(true)} /></div>
  }
  return (
    <form className="mt-3 space-y-3 border-t border-slate-100 pt-3" onSubmit={(e) => { e.preventDefault(); cancelar.mutate() }}>
      {temAluguel && (
        <p className="text-sm text-slate-700">
          {noPrazo ? `Cancelando agora (até ${data(r.cancelavelSemCustoAte)}), a cobrança do aluguel também é cancelada.`
            : 'Passou do prazo para cancelar sem custo: o aluguel continua devido.'}
        </p>
      )}
      <Campo rotulo="Motivo (opcional)" value={motivo} onChange={(e) => setMotivo(e.target.value)} maxLength={500} />
      {gestao && temAluguel && !noPrazo && (
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={dispensar} onChange={(e) => setDispensar(e.target.checked)} className="h-5 w-5 accent-marca-700" />
          Dispensar o aluguel (ex.: o espaço ficou interditado)
        </label>
      )}
      <MensagemDeErro erro={cancelar.error} />
      <div className="flex flex-col gap-2 md:flex-row">
        <Botao type="submit" variante="perigo" carregando={cancelar.isPending}>Confirmar o cancelamento</Botao>
        <Botao type="button" variante="secundario" onClick={() => setAberto(false)}>Voltar</Botao>
      </div>
    </form>
  )
}
