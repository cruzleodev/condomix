import type { ReactNode } from 'react'

export interface Coluna<T> {
  titulo: string
  valor: (linha: T) => ReactNode
  /** Alinha à direita (valores, ações). */
  direita?: boolean
}

/**
 * Lista responsiva: tabela no computador (a partir de 768 px) e cartões no celular.
 * O cartão mostra a primeira coluna em destaque e as demais como "título: valor".
 */
export function Tabela<T>({ linhas, colunas, chave, vazio = 'Nada por aqui.' }:
  { linhas: T[]; colunas: Coluna<T>[]; chave: (linha: T) => string; vazio?: string }) {
  if (linhas.length === 0) return <p className="py-6 text-center text-sm text-slate-500">{vazio}</p>
  const [primeira, ...demais] = colunas
  return (
    <>
      <div className="hidden overflow-x-auto rounded-2xl bg-white shadow-sm ring-1 ring-slate-200 md:block">
        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 text-xs uppercase text-slate-500">
            <tr>{colunas.map((c) => <th key={c.titulo} className={`px-4 py-3 font-semibold ${c.direita ? 'text-right' : ''}`}>{c.titulo}</th>)}</tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {linhas.map((linha) => (
              <tr key={chave(linha)} className="hover:bg-slate-50">
                {colunas.map((c) => <td key={c.titulo} className={`px-4 py-3 align-top ${c.direita ? 'text-right' : ''}`}>{c.valor(linha)}</td>)}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <ul className="space-y-3 md:hidden">
        {linhas.map((linha) => (
          <li key={chave(linha)} className="rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
            <div className="font-medium">{primeira.valor(linha)}</div>
            <dl className="mt-2 space-y-1 text-sm">
              {demais.map((c) => (
                <div key={c.titulo} className="flex items-start justify-between gap-3">
                  <dt className="text-slate-500">{c.titulo}</dt>
                  <dd className="text-right">{c.valor(linha)}</dd>
                </div>
              ))}
            </dl>
          </li>
        ))}
      </ul>
    </>
  )
}
