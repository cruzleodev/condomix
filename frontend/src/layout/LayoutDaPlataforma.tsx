import { Icone, type NomeDoIcone } from '../componentes/Icone'
import { NavLink, Outlet, useLocation } from 'react-router'
import { useSessao } from '../sessao/contexto'

const ITENS: { para: string; rotulo: string; curto?: string; icone: NomeDoIcone; tambem?: string }[] = [
  // Clientes também fica marcado na página de cada cliente (/plataforma/clientes/...).
  { para: '/plataforma', rotulo: 'Clientes', icone: 'predio', tambem: '/plataforma/clientes/' },
  { para: '/plataforma/faturas', rotulo: 'Faturas', icone: 'faturas' },
  { para: '/plataforma/planos', rotulo: 'Planos e preços', curto: 'Planos', icone: 'planos' },
  { para: '/plataforma/logins', rotulo: 'Logins', icone: 'logins' },
  { para: '/plataforma/suporte', rotulo: 'Suporte', icone: 'suporte' },
]

/** Painel do administrador do Condomix: menu lateral no computador e barra embaixo no celular, como no app do condomínio. */
export function LayoutDaPlataforma() {
  const { email, sair, condominios } = useSessao()
  const { pathname } = useLocation()
  const marcado = (item: (typeof ITENS)[number], isActive: boolean) =>
    isActive || ('tambem' in item && !!item.tambem && pathname.startsWith(item.tambem))
  return (
    <div className="min-h-dvh md:flex">
      <aside className="hidden md:fixed md:inset-y-0 md:flex md:w-64 md:flex-col md:bg-slate-900 md:text-white">
        <div className="px-5 py-5">
          <p className="flex items-center gap-2 text-lg font-bold"><img src="/icone.svg" alt="" className="h-8 w-8 rounded-lg" /> Condomix</p>
          <p className="mt-3 text-sm font-medium text-slate-300">Administração da plataforma</p>
        </div>
        <nav aria-label="Menu da plataforma" className="flex-1 space-y-1 px-3">
          {ITENS.map((item) => (
            <NavLink key={item.para} to={item.para} end
              className={({ isActive }) => `flex items-center gap-3 rounded-lg px-3 py-2 text-sm ${marcado(item, isActive) ? 'bg-white/15 font-semibold' : 'text-slate-300 hover:bg-white/10'}`}>
              <Icone nome={item.icone} className="size-5 shrink-0" />{item.rotulo}
            </NavLink>
          ))}
        </nav>
        <div className="space-y-1 border-t border-white/10 px-3 py-3 text-sm">
          {condominios.length > 0 && <NavLink to="/condominio" className="block rounded-lg px-3 py-2 text-slate-300 hover:bg-white/10">Entrar num condomínio</NavLink>}
          <p className="truncate px-3 text-slate-400">{email}</p>
          <button onClick={sair} className="w-full rounded-lg px-3 py-2 text-left text-slate-300 hover:bg-white/10">Sair</button>
        </div>
      </aside>

      <div className="flex min-h-dvh min-w-0 flex-1 flex-col md:ml-64">
        <header className="sticky top-0 z-10 flex items-center justify-between bg-slate-900 px-4 pb-3 pt-[max(0.75rem,var(--area-segura-topo))] text-white md:hidden">
          <p className="font-semibold">Condomix · Plataforma</p>
          <button onClick={sair} className="text-sm text-slate-300 underline">Sair</button>
        </header>
        <main className="mx-auto w-full max-w-6xl flex-1 px-4 pb-28 pt-4 md:px-8 md:py-8">
          <Outlet />
        </main>
      </div>

      <nav aria-label="Menu da plataforma"
        className="fixed inset-x-0 bottom-0 z-10 border-t border-slate-200 bg-white pb-[var(--area-segura-base)] md:hidden">
        <ul className="grid grid-cols-5">
          {ITENS.map((item) => (
            <li key={item.para}>
              <NavLink to={item.para} end
                className={({ isActive }) => `flex min-h-14 flex-col items-center justify-center gap-0.5 text-xs ${marcado(item, isActive) ? 'font-semibold text-slate-900' : 'text-slate-500'}`}>
                <Icone nome={item.icone} className="size-5" />
                {'curto' in item ? item.curto : item.rotulo}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
    </div>
  )
}
