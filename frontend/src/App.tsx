import { createBrowserRouter, Navigate, RouterProvider } from 'react-router'
import { LayoutDoApp } from './layout/LayoutDoApp'
import { RotaDoCondominio, RotaLogada } from './layout/Rotas'
import { AceitarConvite } from './paginas/AceitarConvite'
import { Balancete } from './paginas/Balancete'
import { Conta } from './paginas/Conta'
import { Documentos } from './paginas/Documentos'
import { Entrar } from './paginas/Entrar'
import { EscolherCondominio } from './paginas/EscolherCondominio'
import { Inicio } from './paginas/Inicio'
import { MeusDebitos } from './paginas/MeusDebitos'
import { Plataforma } from './paginas/Plataforma'

const rotas = [
  { path: '/entrar', element: <Entrar /> },
  { path: '/convite', element: <AceitarConvite /> },
  {
    element: <RotaLogada />,
    children: [
      { path: '/condominio', element: <EscolherCondominio /> },
      { path: '/plataforma', element: <Plataforma /> },
    ],
  },
  {
    element: <RotaDoCondominio />,
    children: [{
      element: <LayoutDoApp />,
      children: [
        { path: '/', element: <Inicio /> },
        { path: '/debitos', element: <MeusDebitos /> },
        { path: '/documentos', element: <Documentos /> },
        { path: '/balancete', element: <Balancete /> },
        { path: '/conta', element: <Conta /> },
      ],
    }],
  },
  { path: '*', element: <Navigate to="/" replace /> },
]

const roteador = createBrowserRouter(rotas)

export function App() {
  return <RouterProvider router={roteador} />
}
