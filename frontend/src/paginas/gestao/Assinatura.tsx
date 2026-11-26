import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { api, dados } from '../../api/cliente'
import type { AssinaturaDoCondominio, FaturaDaAssinatura } from '../../api/tipos'
import { DadosParaPagar } from '../../componentes/Boleto'
import { BotaoDeIcone } from '../../componentes/BotaoDeIcone'
import { Tabela } from '../../componentes/Tabela'
import { Cartao, Carregando, Etiqueta, MensagemDeErro, TituloDaPagina } from '../../componentes/ui'
import { useSessao } from '../../sessao/contexto'
import { data, moeda, periodoDaFatura, ROTULOS } from '../../util/formato'

const POR = { MENSAL: 'por mês', SEMESTRAL: 'por semestre', ANUAL: 'por ano' } as const

/**
 * A assinatura do Condomix, para o síndico e a administradora: o plano e as faturas, com Pix e boleto.
 * Com o condomínio suspenso por falta de pagamento, é a única tela que abre; pago, o acesso volta sozinho.
 */
export function Assinatura() {
  const { condominio, condominioId, escolherCondominio } = useSessao()
  const navegar = useNavigate()
  const pendente = !!condominio?.pagamentoPendente
  const assinatura = useQuery({
    queryKey: ['assinatura'],
    queryFn: async () => dados(await api.GET('/api/assinatura')),
    // Esperando a confirmação do pagamento, ou um boleto sendo gerado: confere de novo sozinho.
    refetchInterval: (q) => pendente || q.state.data?.faturas?.some((f) => f.situacao === 'A_EMITIR') ? 5000 : false,
  })
  const pagou = pendente && assinatura.data !== undefined && !assinatura.data.suspensaPorFaltaDePagamento
  useEffect(() => {
    // O pagamento foi confirmado: troca o acesso restrito pelo normal e volta ao início.
    if (pagou && condominioId) escolherCondominio(condominioId).then(() => navegar('/', { replace: true }))
  }, [pagou, condominioId, escolherCondominio, navegar])

  return (
    <div>
      <TituloDaPagina subtitulo="O plano do condomínio no Condomix e as faturas.">Assinatura do Condomix</TituloDaPagina>
      {assinatura.isPending ? <Carregando /> : assinatura.isError ? <MensagemDeErro erro={assinatura.error} /> : (
        <Conteudo a={assinatura.data} />
      )}
    </div>
  )
}

function Conteudo({ a }: { a: AssinaturaDoCondominio }) {
  const faturas = a.faturas ?? []
  const emAberto = faturas.filter((f) => f.situacao !== 'PAGA')
  const pagas = faturas.filter((f) => f.situacao === 'PAGA')
  // A fatura atrasada já abre com o Pix e o boleto; as outras abrem pelo botão da tabela.
  const [pagando, setPagando] = useState<string | null>(emAberto.find((f) => f.atrasada && f.situacao === 'EMITIDA')?.id ?? null)
  const aPagar = emAberto.find((f) => f.id === pagando && f.situacao === 'EMITIDA')
  return (
    <div className="space-y-6">
      {a.suspensaPorFaltaDePagamento && (
        <div role="alert" className="rounded-xl bg-red-50 p-4 text-sm text-red-900 ring-1 ring-red-200">
          <p className="font-semibold">O acesso ao Condomix está suspenso por falta de pagamento.</p>
          <p className="mt-1">
            Moradores e gestão não conseguem entrar até a fatura em atraso ser paga. Pague abaixo, com Pix ou boleto:
            assim que o pagamento for confirmado, o acesso volta sozinho para todos.
          </p>
        </div>
      )}
      <section>
        <h2 className="mb-2 text-sm font-semibold uppercase text-slate-500">Plano</h2>
        <Tabela<AssinaturaDoCondominio>
          linhas={[a]}
          chave={() => 'plano'}
          colunas={[
            { titulo: 'Plano', valor: (x) => <span className="font-medium">{x.plano}</span> },
            { titulo: 'Cobrança', valor: (x) => ROTULOS.periodicidade[x.periodicidade ?? 'MENSAL'] },
            { titulo: 'Valor', valor: (x) => (x.preco != null ? `${moeda(x.preco)} ${POR[x.periodicidade ?? 'MENSAL']}` : 'A combinar'), direita: true },
            {
              titulo: 'Situação',
              valor: (x) => x.status === 'TESTE'
                ? <Etiqueta cor="marca">{x.fimDoTeste ? `Teste grátis até ${data(x.fimDoTeste)}` : 'Teste grátis'}</Etiqueta>
                : <Etiqueta cor={x.status === 'ATIVA' ? 'verde' : 'cinza'}>{ROTULOS.statusDaAssinatura[x.status ?? 'ATIVA']}</Etiqueta>,
            },
          ]}
        />
        {a.status === 'TESTE' && a.fimDoTeste && (
          <p className="mt-2 text-sm text-slate-600">
            Depois do teste, a assinatura passa a ser cobrada{a.preco != null && <>: {moeda(a.preco)} {POR[a.periodicidade ?? 'MENSAL']}</>}.
            A fatura chega aqui, com Pix e boleto.
          </p>
        )}
      </section>
      <section>
        <h2 className="mb-2 text-sm font-semibold uppercase text-slate-500">Faturas em aberto ({emAberto.length})</h2>
        <TabelaDeFaturas faturas={emAberto} vazio="Nenhuma fatura em aberto." aoPagar={setPagando} />
      </section>
      {aPagar && (
        <Cartao titulo={`Pagar a fatura de ${periodoDaFatura(aPagar.periodoInicio, aPagar.periodoFim)}`}
          acao={<BotaoDeIcone icone="fechar" rotulo="Fechar" onClick={() => setPagando(null)} />}>
          <PagamentoDaFatura faturaId={aPagar.id ?? ''} />
        </Cartao>
      )}
      {pagas.length > 0 && (
        <section>
          <h2 className="mb-2 text-sm font-semibold uppercase text-slate-500">Pagas</h2>
          <TabelaDeFaturas faturas={pagas} vazio="" />
        </section>
      )}
    </div>
  )
}

/** As faturas da assinatura: período, vencimento, valor e situação; as que dá para pagar têm o botão de Pix e boleto. */
function TabelaDeFaturas({ faturas, vazio, aoPagar }: {
  faturas: FaturaDaAssinatura[]; vazio: string; aoPagar?: (id: string) => void
}) {
  return (
    <Tabela<FaturaDaAssinatura>
      linhas={faturas}
      chave={(f) => f.id ?? ''}
      vazio={vazio}
      colunas={[
        { titulo: 'Período', valor: (f) => <span className="font-medium">{periodoDaFatura(f.periodoInicio, f.periodoFim)}</span> },
        { titulo: 'Valor', valor: (f) => moeda(f.valor), direita: true },
        { titulo: 'Situação', valor: (f) => <SituacaoDaFatura fatura={f} /> },
        ...(aoPagar ? [{
          titulo: 'Ações', direita: true,
          valor: (f: FaturaDaAssinatura) => f.situacao === 'EMITIDA'
            ? <BotaoDeIcone icone="cobrancas" rotulo="Pagar com Pix ou boleto" onClick={() => aoPagar(f.id ?? '')} />
            : null,
        }] : []),
      ]}
    />
  )
}

function SituacaoDaFatura({ fatura: f }: { fatura: FaturaDaAssinatura }) {
  const etiqueta = f.situacao === 'PAGA' ? <Etiqueta cor="verde">Paga em {data(f.pagoEm)}</Etiqueta>
    : f.atrasada ? <Etiqueta cor="vermelho">Venceu em {data(f.vencimento)}</Etiqueta>
    : f.situacao === 'EMITIDA' ? <Etiqueta cor="amarelo">Vence em {data(f.vencimento)}</Etiqueta>
    : <Etiqueta cor="cinza">{ROTULOS.situacaoDaFatura[f.situacao ?? 'A_EMITIR']}</Etiqueta>
  return (
    <span>
      {etiqueta}
      {f.situacao === 'A_EMITIR' && <span className="mt-1 block text-xs text-slate-500">O boleto está sendo gerado. Volte em instantes.</span>}
      {f.situacao === 'FALHOU' && <span className="mt-1 block text-xs text-slate-500">Boleto ainda indisponível. Fale com o suporte do Condomix.</span>}
    </span>
  )
}

function PagamentoDaFatura({ faturaId }: { faturaId: string }) {
  const via = useQuery({
    queryKey: ['assinatura', 'pagamento', faturaId],
    queryFn: async () => dados(await api.GET('/api/assinatura/faturas/{id}/pagamento', { params: { path: { id: faturaId } } })),
  })
  if (via.isPending) return <Carregando texto="Buscando o boleto…" />
  if (via.isError) return <MensagemDeErro erro={via.error} />
  return <DadosParaPagar v={via.data} />
}
