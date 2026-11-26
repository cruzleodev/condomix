import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { api, dados } from '../../api/cliente'
import type { FaturaDeCliente } from '../../api/tipos'
import { Cartao, Carregando, MensagemDeErro, TituloDaPagina } from '../../componentes/ui'
import { ContaDeRecebimento } from './ContaDeRecebimento'
import { RodarRotina, TabelaDeFaturas } from './TabelaDeFaturas'

const FILTROS: { id: string; rotulo: string; mostra: (f: FaturaDeCliente) => boolean }[] = [
  { id: 'atrasadas', rotulo: 'Em atraso', mostra: (f) => !!f.atrasada },
  { id: 'abertas', rotulo: 'Em aberto', mostra: (f) => !f.atrasada && (f.situacao === 'EMITIDA' || f.situacao === 'A_EMITIR') },
  { id: 'problema', rotulo: 'Com problema', mostra: (f) => f.situacao === 'FALHOU' },
  { id: 'pagas', rotulo: 'Pagas', mostra: (f) => f.situacao === 'PAGA' },
  { id: 'todas', rotulo: 'Todas', mostra: () => true },
]

/** As faturas da assinatura de todos os clientes e a conta do Condomix que as recebe. */
export function Faturas() {
  const [filtro, setFiltro] = useState('atrasadas')
  const faturas = useQuery({
    queryKey: ['plataforma', 'faturas', 'todas'],
    queryFn: async () => dados(await api.GET('/api/plataforma/faturas')),
    refetchInterval: (q) => q.state.data?.some((f) => f.situacao === 'A_EMITIR' || f.situacao === 'A_CANCELAR') ? 5000 : false,
  })
  const lista = faturas.data ?? []
  const escolhido = FILTROS.find((f) => f.id === filtro) ?? FILTROS[0]
  return (
    <div className="space-y-4">
      <TituloDaPagina subtitulo="A assinatura dos clientes, cobrada por boleto e Pix na conta do Condomix.">Faturas</TituloDaPagina>
      <div className="grid gap-4 md:grid-cols-2">
        <ContaDeRecebimento />
        <Cartao titulo="Como a cobrança funciona">
          <ul className="mb-3 list-disc space-y-1 pl-5 text-sm text-slate-600">
            <li>Cliente novo tem 30 dias de teste grátis. Ao fim, a assinatura fica ativa sozinha.</li>
            <li>A fatura sai 10 dias antes de cada período e vence no primeiro dia dele.</li>
            <li>Com 15 dias de atraso, o acesso é suspenso; a gestão ainda entra para pagar, e o acesso volta sozinho.</li>
          </ul>
          <RodarRotina />
        </Cartao>
      </div>
      <nav aria-label="Filtrar faturas" className="flex flex-wrap gap-2">
        {FILTROS.map((f) => (
          <button key={f.id} onClick={() => setFiltro(f.id)} aria-pressed={filtro === f.id}
            className={`shrink-0 rounded-full px-3 py-1 text-sm ring-1 ${filtro === f.id ? 'bg-marca-700 text-white ring-marca-700' : 'bg-white text-slate-700 ring-slate-300'}`}>
            {f.rotulo} ({lista.filter(f.mostra).length})
          </button>
        ))}
      </nav>
      {faturas.isPending ? <Carregando /> : faturas.isError ? <MensagemDeErro erro={faturas.error} /> : (
        <TabelaDeFaturas faturas={lista.filter(escolhido.mostra)} mostrarCliente />
      )}
    </div>
  )
}
