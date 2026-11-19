import { NavLink, Outlet } from 'react-router'
import { useSessao } from '../sessao/contexto'

const ITENS = [
  { para: '/plataforma', rotulo: 'Clientes', icone: '▦' },
  { para: '/plataforma/planos', rotulo: 'Planos e preços', icone: '$' },
  { para: '/plataforma/suporte', rotulo: 'Suporte', icone: '⛑' },
]

/** Painel do administrador do Condomix: menu lateral no computador, abas em cima no celular. */
export function LayoutDaPlataforma() {
  const { email, sair, condominios } = useSessao()
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
              className={({ isActive }) => `flex items-center gap-3 rounded-lg px-3 py-2 text-sm ${isActive ? 'bg-white/15 font-semibold' : 'text-slate-300 hover:bg-white/10'}`}>
              <span aria-hidden className="w-5 text-center">{item.icone}</span>{item.rotulo}
            </NavLink>
          ))}
        </nav>
        <div className="space-y-1 border-t border-white/10 px-3 py-3 text-sm">
          {condominios.length > 0 && <NavLink to="/condominio" className="block rounded-lg px-3 py-2 text-slate-300 hover:bg-white/10">Entrar num condomínio</NavLink>}
          <p className="truncate px-3 text-slate-400">{email}</p>
          <button onClick={sair} className="w-full rounded-lg px-3 py-2 text-left text-slate-300 hover:bg-white/10">Sair</button>
        </div>
      </aside>

      <div className="flex min-h-dvh flex-1 flex-col md:ml-64">
        <header className="sticky top-0 z-10 bg-slate-900 px-4 pb-2 pt-[max(0.75rem,env(safe-area-inset-top))] text-white md:hidden">
          <div className="flex items-center justify-between">
            <p className="font-semibold">Condomix · Plataforma</p>
            <button onClick={sair} className="text-sm text-slate-300 underline">Sair</button>
          </div>
          <nav aria-label="Menu da plataforma" className="mt-2 flex gap-2 overflow-x-auto">
            {ITENS.map((item) => (
              <NavLink key={item.para} to={item.para} end
                className={({ isActive }) => `shrink-0 rounded-full px-3 py-1 text-sm ${isActive ? 'bg-white text-slate-900' : 'bg-white/10'}`}>
                {item.rotulo}
              </NavLink>
            ))}
          </nav>
        </header>
        <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-4 md:px-8 md:py-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
