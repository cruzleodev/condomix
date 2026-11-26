import type { ReactNode } from 'react'
import { Icone, type NomeDoIcone } from './Icone'

/**
 * Botão pequeno com ícone e texto, para ações secundárias dentro de um formulário (ex.: "+ Outro horário"),
 * no lugar de um link sublinhado.
 */
export function BotaoComIcone({ icone, children, onClick, disabled }: {
  icone: NomeDoIcone
  children: ReactNode
  onClick: () => void
  disabled?: boolean
}) {
  return (
    <button type="button" onClick={onClick} disabled={disabled}
      className="inline-flex min-h-11 items-center gap-1.5 rounded-xl border border-marca-700 bg-white px-3 text-sm font-medium text-marca-700 hover:bg-marca-50 disabled:border-slate-300 disabled:text-slate-400">
      <Icone nome={icone} className="size-4" />
      {children}
    </button>
  )
}
