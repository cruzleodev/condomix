import { Navigate, Outlet, useLocation } from 'react-router'
import { useSessao } from '../sessao/contexto'

/**
 * Só deixa passar quem está logado e já escolheu o condomínio. Se o token venceu no meio do uso, o login
 * devolve a pessoa à tela em que estava; se ela clicou em "Sair", não (o próximo login pode ser de outra pessoa).
 */
export function RotaDoCondominio() {
  const { logado, condominioId, tem, saiuPorVontade, condominios } = useSessao()
  const local = useLocation()
  if (!logado) return <Navigate to="/entrar" replace state={saiuPorVontade ? null : { de: local.pathname }} />
  if (!condominioId) {
    const destino = tem('ADMIN_PLATAFORMA') ? '/plataforma' : condominios.length > 0 ? '/condominio' : '/sem-acesso'
    return <Navigate to={destino} replace />
  }
  return <Outlet />
}

/** Telas de quem já está logado, mas ainda sem condomínio (escolha, plataforma). */
export function RotaLogada() {
  const { logado } = useSessao()
  return logado ? <Outlet /> : <Navigate to="/entrar" replace />
}

/** Telas só para alguns papéis (ex.: gestão). Quem não tem o papel volta ao início. */
export function RotaComPapel({ papeis }: { papeis: string[] }) {
  const { tem } = useSessao()
  return tem(...papeis) ? <Outlet /> : <Navigate to="/" replace />
}
