import { Icone, type NomeDoIcone } from '../../componentes/Icone'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { api, dados } from '../../api/cliente'
import { Aviso, TituloDaPagina } from '../../componentes/ui'
import { useSessao } from '../../sessao/contexto'
import { GESTAO } from '../../sessao/papeis'
import { percentual } from '../../util/formato'

const ATALHOS: { para: string; titulo: string; texto: string; icone: NomeDoIcone; papeis?: string[] }[] = [
  { para: '/gestao/unidades', titulo: 'Unidades', icone: 'predio', texto: 'Blocos, apartamentos, salas e frações ideais.' },
  { para: '/gestao/moradores', titulo: 'Moradores', icone: 'pessoas', texto: 'Pessoas, vínculos com as unidades e convites de acesso.' },
  { para: '/gestao/mandatos', titulo: 'Cargos e mandatos', icone: 'mandatos', texto: 'Síndico, subsíndico e conselho: eleição e encerramento.' },
  { para: '/gestao/taxa', titulo: 'Taxa do mês', icone: 'taxa', texto: 'Como a taxa é cobrada, simulação e geração das cobranças.' },
  { para: '/gestao/cobrancas', titulo: 'Cobranças', icone: 'cobrancas', texto: 'Quem pagou, baixa de pagamentos, avulsas e inadimplência.' },
  { para: '/gestao/caixa', titulo: 'Caixa', icone: 'caixa', texto: 'Contas, despesas, transferências e extrato.' },
  { para: '/gestao/espacos', titulo: 'Espaços e reservas', icone: 'espacos', texto: 'Salão, churrasqueira: horários, aluguel, regras e a agenda.' },
  { para: '/balancete', titulo: 'Balancete', icone: 'balancete', texto: 'Prestação de contas do mês.' },
  { para: '/gestao/assinatura', titulo: 'Assinatura do Condomix', icone: 'assinatura', texto: 'O plano do condomínio e as faturas, com Pix e boleto.', papeis: GESTAO },
]

/** Ponto de partida da gestão (no celular, é a aba "Gestão"). */
export function Gestao() {
  const { tem } = useSessao()
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
        {ATALHOS.filter((a) => !a.papeis || tem(...a.papeis)).map((a) => (
          <li key={a.para}>
            <Link to={a.para} className="flex h-full items-start gap-3 rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200 hover:ring-marca-600">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-marca-50 text-marca-700">
                <Icone nome={a.icone} />
              </span>
              <span className="min-w-0">
                <span className="block font-semibold text-slate-900">{a.titulo}</span>
                <span className="block text-sm text-slate-500">{a.texto}</span>
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  )
}
