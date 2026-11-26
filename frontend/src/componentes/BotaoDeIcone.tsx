import { Icone, type NomeDoIcone } from './Icone'

/** De que lado aparece o nome: à esquerda na última coluna das tabelas; em cima ou à direita no começo da linha. */
const POSICAO_DA_DICA = {
  esquerda: 'right-full top-1/2 mr-1 -translate-y-1/2',
  direita: 'left-full top-1/2 ml-1 -translate-y-1/2',
  cima: 'bottom-full left-1/2 mb-1 -translate-x-1/2',
}

/**
 * Ação como ícone (editar, cancelar, bloquear...). No computador, o nome aparece ao passar o mouse (ou ao focar pelo
 * teclado); no celular, onde não há "passar o mouse", o nome fica escrito ao lado do ícone.
 */
export function BotaoDeIcone({ icone, rotulo, perigo, onClick, disabled, dica = 'esquerda' }: {
  icone: NomeDoIcone
  rotulo: string
  perigo?: boolean
  onClick: () => void
  disabled?: boolean
  dica?: keyof typeof POSICAO_DA_DICA
}) {
  return (
    <span className="group relative inline-flex">
      <button type="button" aria-label={rotulo} onClick={onClick} disabled={disabled}
        className={`inline-flex min-h-11 items-center gap-1.5 rounded-xl px-2 text-sm md:size-9 md:min-h-0 md:justify-center md:px-0 disabled:opacity-40 ${perigo
          ? 'text-red-700 hover:bg-red-50' : 'text-marca-700 hover:bg-marca-50'}`}>
        <Icone nome={icone} className="size-5 shrink-0" />
        <span className="md:hidden">{rotulo}</span>
      </button>
      <span role="tooltip" className={`pointer-events-none absolute z-30 hidden whitespace-nowrap rounded-lg bg-slate-900 px-2 py-1 text-xs text-white opacity-0 transition-opacity md:block group-hover:opacity-100 group-focus-within:opacity-100 ${POSICAO_DA_DICA[dica]}`}>
        {rotulo}
      </span>
    </span>
  )
}
