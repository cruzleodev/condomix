import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { api, dados } from '../../api/cliente'
import { Aviso, TituloDaPagina } from '../../componentes/ui'
import { percentual } from '../../util/formato'

const ATALHOS = [
  { para: '/gestao/unidades', titulo: 'Unidades', texto: 'Blocos, apartamentos, salas e frações ideais.' },
  { para: '/gestao/moradores', titulo: 'Moradores', texto: 'Pessoas, vínculos com as unidades e convites de acesso.' },
  { para: '/gestao/mandatos', titulo: 'Cargos e mandatos', texto: 'Síndico, subsíndico e conselho: eleição e encerramento.' },
  { para: '/balancete', titulo: 'Balancete', texto: 'Prestação de contas do mês.' },
]

/** Ponto de partida da gestão (no celular, é a aba "Gestão"). */
export function Gestao() {
  const resumo = useQuery({ queryKey: ['unidades', 'resumo'], queryFn: async () => dados(await api.GET('/api/unidades/resumo')) })
  const r = resumo.data
  return (
    <div>
      <TituloDaPagina subtitulo="Cadastro e administração do condomínio.">Gestão</TituloDaPagina>
      {r && !r.fracoesConferem && (r.ativas ?? 0) > 0 && (
        <div className="mb-4">
          <Aviso>
            As frações ideais das {r.ativas} unidades ativas somam {percentual(r.somaDasFracoes)}
            {(r.semFracaoIdeal ?? 0) > 0 && ` e ${r.semFracaoIdeal} estão sem fração`}. O rateio pela fração ideal precisa de 100%.
          </Aviso>
        </div>
      )}
      <ul className="grid gap-3 md:grid-cols-2">
        {ATALHOS.map((a) => (
          <li key={a.para}>
            <Link to={a.para} className="block h-full rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200 hover:ring-marca-600">
              <p className="font-semibold text-slate-900">{a.titulo}</p>
              <p className="text-sm text-slate-500">{a.texto}</p>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  )
}
