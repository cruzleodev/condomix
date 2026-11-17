import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode } from 'react'

/** Componentes visuais básicos, todos pensados primeiro para o celular. */

export function Botao({ variante = 'principal', carregando, children, className = '', ...resto }:
  ButtonHTMLAttributes<HTMLButtonElement> & { variante?: 'principal' | 'secundario' | 'perigo'; carregando?: boolean }) {
  const cores = {
    principal: 'bg-marca-700 text-white hover:bg-marca-800 disabled:bg-slate-300',
    secundario: 'bg-white text-marca-700 border border-marca-700 hover:bg-marca-50 disabled:text-slate-400 disabled:border-slate-300',
    perigo: 'bg-white text-red-700 border border-red-300 hover:bg-red-50',
  }[variante]
  return (
    <button
      className={`min-h-11 rounded-xl px-4 font-medium transition-colors ${cores} ${className}`}
      disabled={carregando || resto.disabled}
      {...resto}
    >
      {carregando ? 'Aguarde…' : children}
    </button>
  )
}

export function Campo({ rotulo, ajuda, ...resto }: InputHTMLAttributes<HTMLInputElement> & { rotulo: string; ajuda?: string }) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-slate-700">{rotulo}</span>
      <input
        className="min-h-11 w-full rounded-xl border border-slate-300 bg-white px-3 text-base outline-none focus:border-marca-600 focus:ring-2 focus:ring-marca-100"
        {...resto}
      />
      {ajuda && <span className="mt-1 block text-xs text-slate-500">{ajuda}</span>}
    </label>
  )
}

export function Cartao({ titulo, acao, children, className = '' }:
  { titulo?: string; acao?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section className={`rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200 ${className}`}>
      {(titulo || acao) && (
        <div className="mb-3 flex items-center justify-between gap-2">
          {titulo && <h2 className="font-semibold text-slate-900">{titulo}</h2>}
          {acao}
        </div>
      )}
      {children}
    </section>
  )
}

export function TituloDaPagina({ children, subtitulo }: { children: ReactNode; subtitulo?: ReactNode }) {
  return (
    <header className="mb-4">
      <h1 className="text-xl font-bold text-slate-900">{children}</h1>
      {subtitulo && <p className="text-sm text-slate-500">{subtitulo}</p>}
    </header>
  )
}

export function Carregando({ texto = 'Carregando…' }: { texto?: string }) {
  return <p role="status" className="py-6 text-center text-sm text-slate-500">{texto}</p>
}

export function MensagemDeErro({ erro }: { erro: unknown }) {
  if (!erro) return null
  const texto = erro instanceof Error ? erro.message : String(erro)
  return <p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-800 ring-1 ring-red-200">{texto}</p>
}

export function Aviso({ children }: { children: ReactNode }) {
  return <p className="rounded-xl bg-amber-50 p-3 text-sm text-amber-900 ring-1 ring-amber-200">{children}</p>
}

export function Vazio({ children }: { children: ReactNode }) {
  return <p className="py-6 text-center text-sm text-slate-500">{children}</p>
}

export function Etiqueta({ cor = 'cinza', children }: { cor?: 'cinza' | 'verde' | 'vermelho' | 'amarelo' | 'marca'; children: ReactNode }) {
  const cores = {
    cinza: 'bg-slate-100 text-slate-700',
    verde: 'bg-emerald-100 text-emerald-800',
    vermelho: 'bg-red-100 text-red-800',
    amarelo: 'bg-amber-100 text-amber-900',
    marca: 'bg-marca-100 text-marca-800',
  }[cor]
  return <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${cores}`}>{children}</span>
}
