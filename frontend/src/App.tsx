import { createBrowserRouter, Navigate, RouterProvider } from 'react-router'
import { LayoutDaPlataforma } from './layout/LayoutDaPlataforma'
import { LayoutDoApp } from './layout/LayoutDoApp'
import { RotaComPapel, RotaDoCondominio, RotaLogada } from './layout/Rotas'
import { AceitarConvite } from './paginas/AceitarConvite'
import { Balancete } from './paginas/Balancete'
import { Conta } from './paginas/Conta'
import { Documentos } from './paginas/Documentos'
import { Entrar } from './paginas/Entrar'
import { Caixa } from './paginas/gestao/Caixa'
import { Cobrancas } from './paginas/gestao/Cobrancas'
import { Gestao } from './paginas/gestao/Gestao'
import { Mandatos } from './paginas/gestao/Mandatos'
import { Moradores } from './paginas/gestao/Moradores'
import { TaxaDoMes } from './paginas/gestao/TaxaDoMes'
import { Unidades } from './paginas/gestao/Unidades'
import { EscolherCondominio } from './paginas/EscolherCondominio'
import { Inicio } from './paginas/Inicio'
import { MeusDebitos } from './paginas/MeusDebitos'
import { Clientes } from './paginas/plataforma/Clientes'
import { Planos } from './paginas/plataforma/Planos'
import { Suporte } from './paginas/plataforma/Suporte'
import { SemAcesso } from './paginas/SemAcesso'
import { ACOMPANHAMENTO, PLATAFORMA } from './sessao/papeis'

const rotas = [
  { path: '/entrar', element: <Entrar /> },
  { path: '/convite', element: <AceitarConvite /> },
  {
    element: <RotaLogada />,
    children: [
      { path: '/condominio', element: <EscolherCondominio /> },
      { path: '/sem-acesso', element: <SemAcesso /> },
      {
        element: <RotaComPapel papeis={PLATAFORMA} />,
        children: [{
          element: <LayoutDaPlataforma />,
          children: [
            { path: '/plataforma', element: <Clientes /> },
            { path: '/plataforma/planos', element: <Planos /> },
            { path: '/plataforma/suporte', element: <Suporte /> },
          ],
        }],
      },
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
        {
          element: <RotaComPapel papeis={ACOMPANHAMENTO} />,
          children: [
            { path: '/gestao', element: <Gestao /> },
            { path: '/gestao/unidades', element: <Unidades /> },
            { path: '/gestao/moradores', element: <Moradores /> },
            { path: '/gestao/mandatos', element: <Mandatos /> },
            { path: '/gestao/taxa', element: <TaxaDoMes /> },
            { path: '/gestao/cobrancas', element: <Cobrancas /> },
            { path: '/gestao/caixa', element: <Caixa /> },
          ],
        },
      ],
    }],
  },
  { path: '*', element: <Navigate to="/" replace /> },
]

const roteador = createBrowserRouter(rotas)

export function App() {
  return <RouterProvider router={roteador} />
}
