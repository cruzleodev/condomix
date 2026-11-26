import { useEffect, useId, useRef, useState } from 'react'
import { data, dataParaIso, hojeIso, mascaraDeData, mesPorExtenso, semanasDoMes, somarMeses } from '../util/formato'

const SEMANA = ['D', 'S', 'T', 'Q', 'Q', 'S', 'S']

/**
 * Data no formato brasileiro (dd/mm/aaaa), com o mesmo visual em todos os navegadores: aceita só números (as barras
 * entram sozinhas) e tem um calendário próprio no botão ao lado. O valor é o da API ("2026-11-23", ou vazio).
 * Substitui o `<input type="date">`, que muda de cara e de idioma conforme o navegador.
 */
export function CampoData({ rotulo, valor, aoMudar, min, max, required, ajuda }: {
  rotulo: string
  valor: string
  aoMudar: (iso: string) => void
  min?: string
  max?: string
  required?: boolean
  ajuda?: string
}) {
  const [texto, setTexto] = useState(() => (valor ? data(valor) : ''))
  const [aberto, setAberto] = useState(false)
  const [mesDoQuadro, setMesDoQuadro] = useState(() => (valor || hojeIso()).slice(0, 7))
  const caixa = useRef<HTMLDivElement>(null)
  const campo = useRef<HTMLInputElement>(null)
  const id = useId()

  // O valor pode mudar de fora (ex.: o fim do mandato acompanha a posse): o texto acompanha.
  useEffect(() => {
    if (valor !== dataParaIso(texto)) setTexto(valor ? data(valor) : '')
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [valor])

  // Data incompleta, inexistente ou fora do limite: o formulário não é enviado, e o navegador mostra o motivo.
  const problema = problemaDaData(texto, min, max, required)
  useEffect(() => { campo.current?.setCustomValidity(problema ?? '') }, [problema])

  useEffect(() => {
    if (!aberto) return
    const fora = (e: MouseEvent) => { if (!caixa.current?.contains(e.target as Node)) setAberto(false) }
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') setAberto(false) }
    document.addEventListener('mousedown', fora)
    document.addEventListener('keydown', esc)
    return () => { document.removeEventListener('mousedown', fora); document.removeEventListener('keydown', esc) }
  }, [aberto])

  function digitar(novo: string) {
    const formatado = mascaraDeData(novo)
    setTexto(formatado)
    const iso = dataParaIso(formatado)
    if (formatado === '') aoMudar('')
    else if (iso && dentro(iso, min, max)) aoMudar(iso)
  }

  function escolher(iso: string) {
    setTexto(data(iso))
    aoMudar(iso)
    setAberto(false)
    campo.current?.focus()
  }

  function abrir() {
    setMesDoQuadro((dataParaIso(texto) || valor || hojeIso()).slice(0, 7))
    setAberto(!aberto)
  }

  const hoje = hojeIso()
  return (
    <div ref={caixa}>
      <label htmlFor={id} className="mb-1 block text-sm font-medium text-slate-700">{rotulo}</label>
      <div className="relative flex gap-1">
        <input id={id} ref={campo} inputMode="numeric" autoComplete="off" placeholder="dd/mm/aaaa" maxLength={10}
          required={required} value={texto} onChange={(e) => digitar(e.target.value)}
          aria-invalid={problema && texto.length === 10 ? true : undefined}
          className="min-h-11 w-full min-w-0 rounded-xl border border-slate-300 bg-white px-3 text-base outline-none focus:border-marca-600 focus:ring-2 focus:ring-marca-100 aria-invalid:border-red-400" />
        <button type="button" onClick={abrir} aria-label={`Abrir o calendário: ${rotulo}`} aria-expanded={aberto}
          className="min-h-11 min-w-11 rounded-xl border border-slate-300 bg-white text-marca-700 hover:bg-marca-50">
          <IconeCalendario />
        </button>
        {aberto && (
          <div role="dialog" aria-label={`Calendário: ${rotulo}`}
            className="absolute left-0 top-full z-20 mt-1 w-72 max-w-[calc(100vw-2rem)] rounded-2xl bg-white p-3 shadow-lg ring-1 ring-slate-200">
            <div className="mb-2 flex items-center justify-between">
              <BotaoDeSeta rotulo="Mês anterior" onClick={() => setMesDoQuadro(somarMeses(mesDoQuadro, -1))}
                disabled={!!min && somarMeses(mesDoQuadro, -1) < min.slice(0, 7)}>‹</BotaoDeSeta>
              <span className="font-semibold first-letter:uppercase">{mesPorExtenso(mesDoQuadro)}</span>
              <BotaoDeSeta rotulo="Próximo mês" onClick={() => setMesDoQuadro(somarMeses(mesDoQuadro, 1))}
                disabled={!!max && somarMeses(mesDoQuadro, 1) > max.slice(0, 7)}>›</BotaoDeSeta>
            </div>
            <table className="w-full table-fixed text-center text-sm">
              <thead>
                <tr>{SEMANA.map((d, i) => <th key={i} scope="col" className="pb-1 text-xs font-medium text-slate-500">{d}</th>)}</tr>
              </thead>
              <tbody>
                {semanasDoMes(mesDoQuadro).map((semana, i) => (
                  <tr key={i}>
                    {semana.map((dia, j) => (
                      <td key={j} className="p-0.5">
                        {dia && (
                          <button type="button" disabled={!dentro(dia, min, max)} onClick={() => escolher(dia)}
                            aria-label={data(dia)} aria-pressed={dia === valor}
                            className={`size-9 rounded-lg disabled:text-slate-300 ${dia === valor ? 'bg-marca-700 font-semibold text-white'
                              : dia === hoje ? 'font-semibold text-marca-700 ring-1 ring-marca-300 hover:bg-marca-50' : 'text-slate-700 hover:bg-marca-50'}`}>
                            {Number(dia.slice(8))}
                          </button>
                        )}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="mt-2 flex justify-between gap-2">
              <button type="button" disabled={!dentro(hoje, min, max)} onClick={() => escolher(hoje)}
                className="min-h-11 flex-1 rounded-xl text-sm font-medium text-marca-700 hover:bg-marca-50 disabled:text-slate-300">
                Hoje
              </button>
              {!required && (
                <button type="button" onClick={() => { setTexto(''); aoMudar(''); setAberto(false) }}
                  className="min-h-11 flex-1 rounded-xl text-sm font-medium text-slate-600 hover:bg-slate-100">
                  Limpar
                </button>
              )}
            </div>
          </div>
        )}
      </div>
      {problema && texto.length === 10
        ? <span className="mt-1 block text-xs text-red-700">{problema}</span>
        : ajuda && <span className="mt-1 block text-xs text-slate-500">{ajuda}</span>}
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

function IconeCalendario() {
  return (
    <svg aria-hidden viewBox="0 0 24 24" className="mx-auto size-5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
      <rect x="3" y="5" width="18" height="16" rx="2" />
      <path d="M3 10h18M8 3v4M16 3v4" />
    </svg>
  )
}

function dentro(iso: string, min?: string, max?: string): boolean {
  return (!min || iso >= min) && (!max || iso <= max)
}

function problemaDaData(texto: string, min?: string, max?: string, required?: boolean): string | null {
  if (!texto) return required ? 'Preencha a data.' : null
  if (texto.length < 10) return 'Preencha a data completa: dd/mm/aaaa.'
  const iso = dataParaIso(texto)
  if (!iso) return 'Esta data não existe.'
  if (min && iso < min) return `A data não pode ser antes de ${data(min)}.`
  if (max && iso > max) return `A data não pode ser depois de ${data(max)}.`
  return null
}
