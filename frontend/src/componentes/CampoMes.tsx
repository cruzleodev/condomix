import { useEffect, useId, useRef, useState } from 'react'
import { mesAtual, mesPorExtenso, somarMeses } from '../util/formato'

const MESES = ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez']

/**
 * Escolha de mês e ano ("2026-11") sem digitar: setas para o mês anterior e o próximo e, ao tocar no mês, um quadro
 * com os 12 meses do ano. Substitui o `<input type="month">`, que no Safari e no Firefox do computador vira um campo
 * de texto livre e aceita letras.
 * @param permiteVazio mostra "Todos os meses" (valor vazio), para filtros
 */
export function CampoMes({ rotulo, valor, aoMudar, min, max, permiteVazio, ajuda }: {
  rotulo: string
  valor: string
  aoMudar: (anoMes: string) => void
  min?: string
  max?: string
  permiteVazio?: boolean
  ajuda?: string
}) {
  const [aberto, setAberto] = useState(false)
  const [anoDoQuadro, setAnoDoQuadro] = useState(() => anoDe(valor || max || mesAtual()))
  const caixa = useRef<HTMLDivElement>(null)
  const idDoRotulo = useId()

  useEffect(() => {
    if (!aberto) return
    const fora = (e: MouseEvent) => { if (!caixa.current?.contains(e.target as Node)) setAberto(false) }
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') setAberto(false) }
    document.addEventListener('mousedown', fora)
    document.addEventListener('keydown', esc)
    return () => { document.removeEventListener('mousedown', fora); document.removeEventListener('keydown', esc) }
  }, [aberto])

  const permitido = (anoMes: string) => (!min || anoMes >= min) && (!max || anoMes <= max)
  const base = valor || max || mesAtual()
  const anterior = somarMeses(base, -1)
  const proximo = somarMeses(base, 1)

  function escolher(anoMes: string) {
    aoMudar(anoMes)
    setAberto(false)
  }

  function abrir() {
    setAnoDoQuadro(anoDe(base))
    setAberto(!aberto)
  }

  return (
    <div className="block" ref={caixa}>
      <span id={idDoRotulo} className="mb-1 block text-sm font-medium text-slate-700">{rotulo}</span>
      <div className="relative flex items-stretch gap-1" role="group" aria-labelledby={idDoRotulo}>
        <BotaoDeSeta rotulo="Mês anterior" disabled={!permitido(anterior)} onClick={() => aoMudar(anterior)}>‹</BotaoDeSeta>
        <button type="button" onClick={abrir} aria-haspopup="dialog" aria-expanded={aberto}
          className="flex min-h-11 min-w-0 flex-1 items-center justify-between gap-1 rounded-xl border border-slate-300 bg-white px-3 text-left text-base outline-none focus:border-marca-600 focus:ring-2 focus:ring-marca-100">
          <span className="truncate">{valor ? maiuscula(mesPorExtenso(valor)) : 'Todos os meses'}</span>
          <span aria-hidden className="shrink-0 text-slate-400">▾</span>
        </button>
        <BotaoDeSeta rotulo="Próximo mês" disabled={!permitido(proximo)} onClick={() => aoMudar(proximo)}>›</BotaoDeSeta>
        {aberto && (
          <div role="dialog" aria-label={`Escolher ${rotulo.toLowerCase()}`}
            className="absolute left-0 top-full z-20 mt-1 w-72 max-w-[calc(100vw-2rem)] rounded-2xl bg-white p-3 shadow-lg ring-1 ring-slate-200">
            <div className="mb-2 flex items-center justify-between">
              <BotaoDeSeta rotulo="Ano anterior" disabled={!!min && `${anoDoQuadro - 1}-12` < min}
                onClick={() => setAnoDoQuadro(anoDoQuadro - 1)}>‹</BotaoDeSeta>
              <span className="font-semibold">{anoDoQuadro}</span>
              <BotaoDeSeta rotulo="Próximo ano" disabled={!!max && `${anoDoQuadro + 1}-01` > max}
                onClick={() => setAnoDoQuadro(anoDoQuadro + 1)}>›</BotaoDeSeta>
            </div>
            <div className="grid grid-cols-3 gap-2">
              {MESES.map((nome, i) => {
                const anoMes = `${anoDoQuadro}-${String(i + 1).padStart(2, '0')}`
                const escolhido = anoMes === valor
                return (
                  <button key={nome} type="button" disabled={!permitido(anoMes)} onClick={() => escolher(anoMes)}
                    aria-label={mesPorExtenso(anoMes)} aria-pressed={escolhido}
                    className={`min-h-11 rounded-xl text-sm font-medium disabled:text-slate-300 ${escolhido
                      ? 'bg-marca-700 text-white' : 'text-slate-700 hover:bg-marca-50'}`}>
                    {nome}
                  </button>
                )
              })}
            </div>
            {permiteVazio && (
              <button type="button" onClick={() => escolher('')}
                className="mt-2 min-h-11 w-full rounded-xl text-sm font-medium text-marca-700 hover:bg-marca-50">
                Todos os meses
              </button>
            )}
          </div>
        )}
      </div>
      {ajuda && <span className="mt-1 block text-xs text-slate-500">{ajuda}</span>}
    </div>
  )
}

function BotaoDeSeta({ rotulo, children, ...resto }: { rotulo: string; children: string; disabled?: boolean; onClick: () => void }) {
  return (
    <button type="button" aria-label={rotulo} {...resto}
      className="min-h-11 min-w-11 rounded-xl border border-slate-300 bg-white text-lg text-marca-700 hover:bg-marca-50 disabled:text-slate-300 disabled:hover:bg-white">
      {children}
    </button>
  )
}

function anoDe(anoMes: string): number {
  return Number(anoMes.slice(0, 4))
}

function maiuscula(texto: string): string {
  return texto.charAt(0).toUpperCase() + texto.slice(1)
}
