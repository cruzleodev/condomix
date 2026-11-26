import type { ReactNode } from 'react'

/** Ícones das ações (traço, 24×24, no estilo Lucide). */
const ICONES = {
  segundaVia: <><path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z" /><path d="M14 2v4a2 2 0 0 0 2 2h4" /><path d="M10 9H8M16 13H8M16 17H8" /></>,
  simular: <path d="M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z" />,
  tentarDeNovo: <><path d="M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8" /><path d="M21 3v5h-5" /></>,
  receber: <><rect width="20" height="12" x="2" y="6" rx="2" /><circle cx="12" cy="12" r="2" /><path d="M6 12h.01M18 12h.01" /></>,
  cancelar: <><circle cx="12" cy="12" r="10" /><path d="m15 9-6 6M9 9l6 6" /></>,
  estornar: <><path d="M9 14 4 9l5-5" /><path d="M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5a5.5 5.5 0 0 1-5.5 5.5H11" /></>,
} satisfies Record<string, ReactNode>

/**
 * Ação de tabela como ícone. No computador, o nome aparece ao passar o mouse (ou ao focar pelo teclado);
 * no celular, onde não há "passar o mouse", o nome fica escrito ao lado do ícone.
 */
export function BotaoDeIcone({ icone, rotulo, perigo, onClick, disabled }: {
  icone: keyof typeof ICONES
  rotulo: string
  perigo?: boolean
  onClick: () => void
  disabled?: boolean
}) {
  return (
    <span className="group relative inline-flex">
      <button type="button" aria-label={rotulo} onClick={onClick} disabled={disabled}
        className={`inline-flex min-h-11 items-center gap-1.5 rounded-xl px-2 text-sm md:size-9 md:min-h-0 md:justify-center md:px-0 disabled:opacity-40 ${perigo
          ? 'text-red-700 hover:bg-red-50' : 'text-marca-700 hover:bg-marca-50'}`}>
        <svg aria-hidden viewBox="0 0 24 24" className="size-5 shrink-0" fill="none" stroke="currentColor" strokeWidth="2"
          strokeLinecap="round" strokeLinejoin="round">{ICONES[icone]}</svg>
        <span className="md:hidden">{rotulo}</span>
      </button>
      {/* À esquerda do ícone: as ações ficam na última coluna, e em cima seria cortado pelo cabeçalho da tabela. */}
      <span role="tooltip" className="pointer-events-none absolute right-full top-1/2 z-20 mr-1 hidden -translate-y-1/2 whitespace-nowrap rounded-lg bg-slate-900 px-2 py-1 text-xs text-white opacity-0 transition-opacity md:block group-hover:opacity-100 group-focus-within:opacity-100">
        {rotulo}
      </span>
    </span>
  )
}
