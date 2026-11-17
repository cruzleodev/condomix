import { NavLink, Outlet } from 'react-router'
import { useSessao } from '../sessao/contexto'

const ABAS = [
  { para: '/', rotulo: 'Início', icone: '⌂' },
  { para: '/debitos', rotulo: 'Débitos', icone: 'R$' },
  { para: '/documentos', rotulo: 'Documentos', icone: '▤' },
  { para: '/balancete', rotulo: 'Balancete', icone: '◔' },
  { para: '/conta', rotulo: 'Conta', icone: '●' },
]

/** Cabeçalho com o condomínio atual, o conteúdo da página e a navegação fixa embaixo (padrão de app no celular). */
export function LayoutDoApp() {
  const { condominio } = useSessao()
  return (
    <div className="mx-auto flex min-h-dvh max-w-2xl flex-col">
      <header className="sticky top-0 z-10 bg-marca-700 px-4 pb-3 pt-[max(0.75rem,env(safe-area-inset-top))] text-white shadow">
        <p className="text-xs uppercase tracking-wide text-marca-100">Condomix</p>
        <p className="truncate font-semibold">{condominio?.nome ?? 'Condomínio'}</p>
      </header>
      <main className="flex-1 px-4 pb-28 pt-4">
        <Outlet />
      </main>
      <nav
        aria-label="Navegação principal"
        className="fixed inset-x-0 bottom-0 z-10 border-t border-slate-200 bg-white pb-[env(safe-area-inset-bottom)]"
      >
        <ul className="mx-auto grid max-w-2xl grid-cols-5">
          {ABAS.map((aba) => (
            <li key={aba.para}>
              <NavLink
                to={aba.para}
                end={aba.para === '/'}
                className={({ isActive }) =>
                  `flex min-h-14 flex-col items-center justify-center gap-0.5 text-xs ${isActive ? 'font-semibold text-marca-700' : 'text-slate-500'}`
                }
              >
                <span aria-hidden className="text-base leading-none">{aba.icone}</span>
                {aba.rotulo}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
    </div>
  )
}
