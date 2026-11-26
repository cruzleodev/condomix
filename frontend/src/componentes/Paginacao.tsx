import { SELECAO, SETA } from './estiloDaSelecao'
import { Botao } from './ui'

/** As opções de linhas por página. "Todas" pede o máximo que a API devolve de uma vez. */
const TAMANHOS_DE_PAGINA = [10, 20, 50] as const
const TODAS_AS_LINHAS = 1000

/**
 * Rodapé de uma lista paginada: quantas linhas por página (10, 20, 50 ou todas), o total e a navegação.
 * Trocar o tamanho volta para a primeira página.
 */
export function Paginacao({ pagina, totalPaginas, total, tamanho, aoMudarPagina, aoMudarTamanho, rotuloDoTotal }: {
  pagina: number
  totalPaginas: number
  total: number
  tamanho: number
  aoMudarPagina: (pagina: number) => void
  aoMudarTamanho: (tamanho: number) => void
  /** Ex.: (n) => `${n} cobranças`. */
  rotuloDoTotal: (total: number) => string
}) {
  return (
    <div className="flex flex-col items-center gap-3 text-sm md:flex-row md:justify-between">
      <div className="flex items-center gap-2 text-slate-600">
        <label htmlFor="linhas-por-pagina">Linhas por página</label>
        <select id="linhas-por-pagina" value={tamanho} onChange={(e) => aoMudarTamanho(Number(e.target.value))}
          className={`min-h-11 rounded-xl border border-slate-300 bg-white px-3 text-slate-900 ${SELECAO}`} style={SETA}>
          {TAMANHOS_DE_PAGINA.map((t) => <option key={t} value={t}>{t}</option>)}
          <option value={TODAS_AS_LINHAS}>Todas</option>
        </select>
        <span>· {rotuloDoTotal(total)}</span>
      </div>
      {totalPaginas > 1 && (
        <div className="flex items-center gap-3">
          <Botao variante="secundario" disabled={pagina === 0} onClick={() => aoMudarPagina(pagina - 1)}>Anterior</Botao>
          <span>Página {pagina + 1} de {totalPaginas}</span>
          <Botao variante="secundario" disabled={pagina + 1 >= totalPaginas} onClick={() => aoMudarPagina(pagina + 1)}>Próxima</Botao>
        </div>
      )}
    </div>
  )
}
