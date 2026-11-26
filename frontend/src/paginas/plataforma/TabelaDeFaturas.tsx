import { BotaoDeIcone } from '../../componentes/BotaoDeIcone'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { api, dados } from '../../api/cliente'
import type { FaturaDeCliente } from '../../api/tipos'
import { DadosParaPagar } from '../../componentes/Boleto'
import { Tabela } from '../../componentes/Tabela'
import { Botao, Cartao, Carregando, Etiqueta, MensagemDeErro } from '../../componentes/ui'
import { data, moeda, periodoDaFatura, ROTULOS } from '../../util/formato'

/** A situação da fatura, numa etiqueta; a atrasada mostra desde quando. */
export function EtiquetaDaFatura({ fatura: f }: { fatura: FaturaDeCliente }) {
  if (f.atrasada) return <Etiqueta cor="vermelho">Atrasada desde {data(f.vencimento)}</Etiqueta>
  const cor = { A_EMITIR: 'amarelo', EMITIDA: 'marca', FALHOU: 'vermelho', PAGA: 'verde', A_CANCELAR: 'cinza', CANCELADA: 'cinza' } as const
  const situacao = f.situacao ?? 'A_EMITIR'
  return <Etiqueta cor={cor[situacao]}>{situacao === 'PAGA' ? `Paga em ${data(f.pagoEm)}` : ROTULOS.situacaoDaFatura[situacao]}</Etiqueta>
}

/** As faturas, com as ações do administrador: 2ª via, tentar de novo, cancelar e (só no simulado) simular o pagamento. */
export function TabelaDeFaturas({ faturas, mostrarCliente }: { faturas: FaturaDeCliente[]; mostrarCliente: boolean }) {
  const [segundaVia, setSegundaVia] = useState<FaturaDeCliente | null>(null)
  return (
    <div className="space-y-3">
      <Tabela<FaturaDeCliente>
        linhas={faturas}
        chave={(f) => f.id ?? ''}
        vazio="Nenhuma fatura aqui."
        colunas={[
          ...(mostrarCliente ? [{
            titulo: 'Cliente',
            valor: (f: FaturaDeCliente) => <Link to={`/plataforma/clientes/${f.condominioId}`} className="font-medium text-marca-700 underline">{f.condominio}</Link>,
          }] : []),
          { titulo: 'Período', valor: (f) => <span className={mostrarCliente ? '' : 'font-medium'}>{periodoDaFatura(f.periodoInicio, f.periodoFim)}</span> },
          { titulo: 'Plano', valor: (f) => `${f.plano} · ${ROTULOS.periodicidade[f.periodicidade ?? 'MENSAL']}` },
          { titulo: 'Valor', valor: (f) => moeda(f.valor), direita: true },
          { titulo: 'Vencimento', valor: (f) => data(f.vencimento) },
          {
            titulo: 'Situação',
            valor: (f) => (
              <span className="space-y-1">
                <EtiquetaDaFatura fatura={f} />
                {f.situacao === 'FALHOU' && f.erro && <span className="block text-xs text-red-700">{f.erro}</span>}
                {f.atrasada && f.suspensaoEm && <span className="block text-xs text-slate-500">Suspende em {data(f.suspensaoEm)}</span>}
              </span>
            ),
          },
          { titulo: 'Ações', valor: (f) => <AcoesDaFatura fatura={f} aoPedirSegundaVia={() => setSegundaVia(f)} />, direita: true },
        ]}
      />
      {segundaVia && (
        <Cartao titulo={`2ª via · ${segundaVia.condominio}`}
          acao={<BotaoDeIcone icone="fechar" rotulo="Fechar" onClick={() => setSegundaVia(null)} />}>
          <SegundaViaDaFatura faturaId={segundaVia.id ?? ''} />
        </Cartao>
      )}
    </div>
  )
}

function AcoesDaFatura({ fatura: f, aoPedirSegundaVia }: { fatura: FaturaDeCliente; aoPedirSegundaVia: () => void }) {
  const cache = useQueryClient()
  const caminho = { params: { path: { id: f.id ?? '' } } }
  const atualizar = () => {
    cache.invalidateQueries({ queryKey: ['plataforma', 'faturas'] })
    cache.invalidateQueries({ queryKey: ['plataforma', 'condominio', f.condominioId] })
    cache.invalidateQueries({ queryKey: ['plataforma', 'historico', f.condominioId] })
    cache.invalidateQueries({ queryKey: ['plataforma', 'condominios'] })
  }
  const acao = useMutation({
    mutationFn: async (qual: 'simular' | 'tentar' | 'cancelar') => {
      if (qual === 'simular') dados(await api.POST('/api/plataforma/faturas/{id}/pagamento-simulado', caminho))
      else if (qual === 'tentar') dados(await api.POST('/api/plataforma/faturas/{id}/nova-tentativa', caminho))
      else dados(await api.POST('/api/plataforma/faturas/{id}/cancelamento', caminho))
    },
    onSuccess: atualizar,
  })
  const emAberto = f.situacao === 'EMITIDA'
  const podeCancelar = f.situacao === 'A_EMITIR' || f.situacao === 'EMITIDA' || f.situacao === 'FALHOU'
  return (
    <span className="inline-flex flex-col items-end gap-1 md:items-center">
      <span className="flex flex-wrap justify-end md:justify-center gap-1 md:flex-nowrap">
        {emAberto && <BotaoDeIcone icone="segundaVia" rotulo="2ª via da fatura" onClick={aoPedirSegundaVia} />}
        {emAberto && f.gateway === 'SIMULADO' && (
          <BotaoDeIcone icone="simular" rotulo="Simular pagamento (testes)" disabled={acao.isPending} onClick={() => acao.mutate('simular')} />
        )}
        {f.situacao === 'FALHOU' && (
          <BotaoDeIcone icone="tentarDeNovo" rotulo="Tentar emitir de novo" disabled={acao.isPending} onClick={() => acao.mutate('tentar')} />
        )}
        {podeCancelar && (
          <BotaoDeIcone icone="cancelar" rotulo="Cancelar a fatura" perigo disabled={acao.isPending} onClick={() => {
            if (window.confirm(`Cancelar a fatura de ${moeda(f.valor)} de ${f.condominio}? O período não será cobrado.`)) acao.mutate('cancelar')
          }} />
        )}
      </span>
      <MensagemDeErro erro={acao.error} />
    </span>
  )
}

function SegundaViaDaFatura({ faturaId }: { faturaId: string }) {
  const via = useQuery({
    queryKey: ['plataforma', 'faturas', 'pagamento', faturaId],
    queryFn: async () => dados(await api.GET('/api/plataforma/faturas/{id}/pagamento', { params: { path: { id: faturaId } } })),
  })
  if (via.isPending) return <Carregando texto="Buscando o boleto…" />
  if (via.isError) return <MensagemDeErro erro={via.error} />
  return <DadosParaPagar v={via.data} />
}

/** Botão para não esperar a rotina de hora em hora (fim dos testes, faturas novas, suspensões). */
export function RodarRotina() {
  const cache = useQueryClient()
  const rotina = useMutation({
    mutationFn: async () => dados(await api.POST('/api/plataforma/faturas/rotina')),
    onSuccess: () => {
      cache.invalidateQueries({ queryKey: ['plataforma'] })
    },
  })
  const r = rotina.data
  return (
    <span className="flex flex-col items-start gap-1">
      <Botao variante="secundario" carregando={rotina.isPending} onClick={() => rotina.mutate()}>Rodar a cobrança agora</Botao>
      {r && (
        <span className="text-xs text-slate-500">
          {r.testesEncerrados} teste(s) encerrado(s), {r.faturasGeradas} fatura(s) gerada(s), {r.condominiosSuspensos} suspenso(s).
          {(r.faturasGeradas ?? 0) > 0 && ' Os boletos ficam prontos em alguns segundos.'}
        </span>
      )}
      <MensagemDeErro erro={rotina.error} />
    </span>
  )
}
