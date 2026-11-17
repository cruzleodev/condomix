import { useState, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react'
import { Botao } from './ui'

const ESTILO = 'min-h-11 w-full rounded-xl border border-slate-300 bg-white px-3 text-base outline-none focus:border-marca-600 focus:ring-2 focus:ring-marca-100'

export function Selecao({ rotulo, opcoes, vazio, ...resto }: SelectHTMLAttributes<HTMLSelectElement> & {
  rotulo: string
  opcoes: { valor: string; rotulo: string }[]
  /** Texto da opção vazia (ex.: "Escolha…"); sem ele, não há opção vazia. */
  vazio?: string
}) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-slate-700">{rotulo}</span>
      <select className={ESTILO} {...resto}>
        {vazio !== undefined && <option value="">{vazio}</option>}
        {opcoes.map((o) => <option key={o.valor} value={o.valor}>{o.rotulo}</option>)}
      </select>
    </label>
  )
}

export function AreaDeTexto({ rotulo, ajuda, ...resto }: TextareaHTMLAttributes<HTMLTextAreaElement> & { rotulo: string; ajuda?: string }) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-slate-700">{rotulo}</span>
      <textarea className={`${ESTILO} py-2`} rows={3} {...resto} />
      {ajuda && <span className="mt-1 block text-xs text-slate-500">{ajuda}</span>}
    </label>
  )
}

/** Mostra o link do convite com um botão para copiar (o envio por e-mail chega na etapa de notificações). */
export function LinkDeConvite({ link, email }: { link: string; email?: string }) {
  const [copiado, setCopiado] = useState(false)
  async function copiar() {
    try {
      await navigator.clipboard.writeText(link)
      setCopiado(true)
      setTimeout(() => setCopiado(false), 2500)
    } catch {
      window.prompt('Copie o link do convite:', link)
    }
  }
  return (
    <div className="rounded-xl bg-emerald-50 p-3 text-sm ring-1 ring-emerald-200">
      <p className="font-medium text-emerald-900">Convite criado{email ? ` para ${email}` : ''}.</p>
      <p className="mb-2 text-emerald-800">Envie este link à pessoa (vale 7 dias):</p>
      <p className="mb-2 break-all rounded-lg bg-white p-2 font-mono text-xs text-slate-700">{link}</p>
      <Botao type="button" variante="secundario" onClick={copiar} className="w-full md:w-auto">
        {copiado ? 'Copiado ✓' : 'Copiar link'}
      </Botao>
    </div>
  )
}
