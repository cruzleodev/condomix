import { Icone } from '../componentes/Icone'
import { useQueryClient } from '@tanstack/react-query'
import { NavLink, Outlet, useNavigate } from 'react-router'
import { AvisoDaAssinatura } from '../componentes/AvisoDaAssinatura'
import { Sino } from '../componentes/Sino'
import { useSessao } from '../sessao/contexto'
import { itensDoCelular, itensVisiveis, type ItemDeNavegacao } from './navegacao'

/**
 * Layout responsivo, para todos os usuários:
 * - celular: cabeçalho em cima, conteúdo e navegação fixa embaixo;
 * - computador (a partir de 768 px): menu lateral fixo e conteúdo largo.
 */
export function LayoutDoApp() {
  const { condominio, perfis, email, suporte, sairDoSuporte } = useSessao()
  const navegar = useNavigate()
  const cache = useQueryClient()
  const pagamentoPendente = !!condominio?.pagamentoPendente
  // No modo suporte e com o acesso suspenso, as notificações não aparecem (a API também não deixa).
  const comSino = !suporte && !pagamentoPendente
  const principais = itensVisiveis(perfis, pagamentoPendente).filter((i) => i.secao !== 'gestao' && i.para !== '/conta')
  const gestao = itensVisiveis(perfis, pagamentoPendente).filter((i) => i.secao === 'gestao')
  return (
    <div className="min-h-dvh md:flex">
      <aside className="hidden md:fixed md:inset-y-0 md:flex md:w-64 md:flex-col md:bg-marca-800 md:text-white">
        <div className="px-5 py-5">
          <p className="flex items-center gap-2 text-lg font-bold"><img src="/icone.svg" alt="" className="h-8 w-8 rounded-lg" /> Condomix</p>
          <div className="mt-3 flex items-center justify-between gap-2">
            <p className="truncate text-sm font-medium text-marca-100" title={condominio?.nome}>{condominio?.nome}</p>
            {comSino && <Sino claro />}
          </div>
        </div>
        <nav aria-label="Menu" className="flex-1 space-y-1 overflow-y-auto px-3">
          {principais.map((item) => <ItemLateral key={item.para} item={item} />)}
          {gestao.length > 0 && (
            <>
              <p className="px-3 pb-1 pt-5 text-xs font-semibold uppercase tracking-wide text-marca-100/70">Gestão</p>
              {gestao.map((item) => <ItemLateral key={item.para} item={item} />)}
            </>
          )}
        </nav>
        <div className="border-t border-white/10 px-3 py-3">
          <ItemLateral item={{ para: '/conta', rotulo: email ?? 'Conta', icone: 'conta' }} />
        </div>
      </aside>

      <div className="flex min-h-dvh min-w-0 flex-1 flex-col md:ml-64">
        {/*
          * O topo fica num bloco só, preso no alto: a faixa do suporte (quando houver) e o cabeçalho do celular.
          * Quem estiver mais em cima desconta a área do relógio e da câmera, para nada ficar atrás deles.
          */}
        <div className="sticky top-0 z-20">
          {suporte && (
            <div role="status" className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 bg-amber-400 px-4 pb-2 pt-[max(0.5rem,var(--area-segura-topo))] text-sm text-amber-950">
              <span><strong>Modo suporte</strong> · somente leitura · até {suporte.expiraEm.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })} · acesso registrado</span>
              <button className="font-semibold underline" onClick={() => { cache.clear(); sairDoSuporte(); navegar('/plataforma') }}>Voltar ao painel</button>
            </div>
          )}
          <header className={`flex items-center justify-between gap-2 bg-marca-700 px-4 pb-3 text-white shadow md:hidden ${suporte ? 'pt-3' : 'pt-[max(0.75rem,var(--area-segura-topo))]'}`}>
            <div className="min-w-0">
              <p className="text-xs uppercase tracking-wide text-marca-100">Condomix</p>
              <p className="truncate font-semibold">{condominio?.nome ?? 'Condomínio'}</p>
            </div>
            {comSino && <Sino claro />}
          </header>
        </div>
        <main className="mx-auto w-full max-w-6xl flex-1 px-4 pb-28 pt-4 md:px-8 md:pb-10 md:pt-8">
          {!suporte && !pagamentoPendente && <AvisoDaAssinatura />}
          <Outlet />
        </main>
      </div>

      <nav aria-label="Navegação principal"
        className="fixed inset-x-0 bottom-0 z-10 border-t border-slate-200 bg-white pb-[var(--area-segura-base)] md:hidden">
        <ul className={`grid ${pagamentoPendente ? 'grid-cols-2' : 'grid-cols-5'}`}>
          {itensDoCelular(perfis, pagamentoPendente).map((item) => (
            <li key={item.para}>
              <NavLink to={item.para} end={item.para === '/'}
                className={({ isActive }) => `flex min-h-14 flex-col items-center justify-center gap-0.5 text-xs ${isActive ? 'font-semibold text-marca-700' : 'text-slate-500'}`}>
                <Icone nome={item.icone} />
                {item.rotulo === 'Meus débitos' ? 'Débitos' : item.rotulo}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
    </div>
  )
}

function ItemLateral({ item }: { item: ItemDeNavegacao }) {
  return (
    <NavLink to={item.para} end={item.para === '/' || item.para === '/gestao'}
      className={({ isActive }) => `flex items-center gap-3 truncate rounded-lg px-3 py-2 text-sm ${isActive ? 'bg-white/15 font-semibold text-white' : 'text-marca-50 hover:bg-white/10'}`}>
      <Icone nome={item.icone} className="size-5 shrink-0" />
      <span className="truncate">{item.rotulo}</span>
    </NavLink>
  )
}
