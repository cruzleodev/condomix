import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { api, dados, requisicaoAutenticada } from '../../api/cliente'
import type { Cobranca } from '../../api/tipos'
import type { components } from '../../api/schema'
import { BarraDoGateway, EtiquetaDoBoleto, SegundaVia } from '../../componentes/Boleto'
import { atualizarEnquantoProcessa } from '../../util/boleto'
import { Selecao } from '../../componentes/formulario'
import { Tabela } from '../../componentes/Tabela'
import { Aviso, Botao, Campo, Cartao, Carregando, Etiqueta, MensagemDeErro, TituloDaPagina } from '../../componentes/ui'
import { useSessao } from '../../sessao/contexto'
import { GESTAO } from '../../sessao/papeis'
import { data, hojeIso, lerReais, moeda, opcoes, ROTULOS } from '../../util/formato'

type Inadimplente = components['schemas']['Inadimplente']
type Formulario = 'nenhum' | 'avulsa' | 'importar' | { pagar: Cobranca } | { segundaVia: Cobranca }

const COR_DO_STATUS = { ABERTA: 'amarelo', PAGA: 'verde', CANCELADA: 'cinza' } as const

/** Cobranças do condomínio (com baixa, estorno, avulsa e importação) e a inadimplência. */
export function Cobrancas() {
  const [aba, setAba] = useState<'cobrancas' | 'inadimplencia'>('cobrancas')
  return (
    <div className="space-y-4">
      <TituloDaPagina subtitulo="Valores em aberto já com multa e juros até hoje.">Cobranças</TituloDaPagina>
      <div role="tablist" className="flex gap-2">
        {([['cobrancas', 'Cobranças'], ['inadimplencia', 'Inadimplência']] as const).map(([valor, rotulo]) => (
          <button key={valor} role="tab" aria-selected={aba === valor} onClick={() => setAba(valor)}
            className={`rounded-full px-4 py-2 text-sm ${aba === valor ? 'bg-marca-700 text-white' : 'bg-white text-slate-700 ring-1 ring-slate-300'}`}>
            {rotulo}
          </button>
        ))}
      </div>
      {aba === 'cobrancas' ? <ListaDeCobrancas /> : <Inadimplencia />}
    </div>
  )
}

function ListaDeCobrancas() {
  const { tem } = useSessao()
  const gerencia = tem(...GESTAO)
  const cliente = useQueryClient()
  const [mes, setMes] = useState('')
  const [status, setStatus] = useState('')
  const [pagina, setPagina] = useState(0)
  const [formulario, setFormulario] = useState<Formulario>('nenhum')

  const cobrancas = useQuery({
    queryKey: ['cobrancas', 'lista', mes, status, pagina],
    queryFn: async () => dados(await api.GET('/api/cobrancas', {
      params: { query: { competencia: mes || undefined, status: (status || undefined) as 'ABERTA' | undefined, pagina, tamanho: 50 } },
    })),
    refetchInterval: (q) => atualizarEnquantoProcessa(q.state.data?.itens),
  })
  const atualizar = () => cliente.invalidateQueries({ queryKey: ['cobrancas'] })
  const estornar = useMutation({
    mutationFn: async (id: string) => dados(await api.POST('/api/cobrancas/{id}/estorno', { params: { path: { id } } })),
    onSuccess: atualizar,
  })
  const cancelar = useMutation({
    mutationFn: async ({ id, motivo }: { id: string; motivo: string }) =>
      dados(await api.POST('/api/cobrancas/{id}/cancelamento', { params: { path: { id } }, body: { motivo } })),
    onSuccess: atualizar,
  })
  const tentarDeNovo = useMutation({
    mutationFn: async (id: string) => dados(await api.POST('/api/cobrancas/{id}/boleto/nova-tentativa', { params: { path: { id } } })),
    onSuccess: atualizar,
  })
  const simular = useMutation({
    mutationFn: async (id: string) => dados(await api.POST('/api/cobrancas/{id}/pagamento-simulado', { params: { path: { id } }, body: {} })),
    onSuccess: () => {
      atualizar()
      cliente.invalidateQueries({ queryKey: ['caixa'] })
    },
  })
  const p = cobrancas.data

  return (
    <div className="space-y-4">
      <BarraDoGateway gerencia={gerencia} />
      {gerencia && formulario === 'nenhum' && (
        <div className="flex flex-wrap gap-2">
          <Botao onClick={() => setFormulario('avulsa')}>Cobrança avulsa</Botao>
          <Botao variante="secundario" onClick={() => setFormulario('importar')}>Importar planilha</Botao>
        </div>
      )}
      {formulario === 'avulsa' && <Avulsa aoTerminar={() => setFormulario('nenhum')} />}
      {formulario === 'importar' && <Importacao aoTerminar={() => setFormulario('nenhum')} />}
      {typeof formulario === 'object' && 'pagar' in formulario && <Pagamento cobranca={formulario.pagar} aoTerminar={() => setFormulario('nenhum')} />}
      {typeof formulario === 'object' && 'segundaVia' in formulario && (
        <Cartao titulo={`2ª via · ${formulario.segundaVia.unidade} · ${formulario.segundaVia.descricao}`}>
          <SegundaVia cobrancaId={formulario.segundaVia.id ?? ''} dono="gestao" />
          <Botao variante="secundario" className="mt-4 w-full md:w-auto" onClick={() => setFormulario('nenhum')}>Fechar</Botao>
        </Cartao>
      )}

      <div className="grid grid-cols-2 gap-3 md:max-w-lg">
        <Campo rotulo="Mês" type="month" value={mes} onChange={(e) => { setMes(e.target.value); setPagina(0) }} ajuda="Vazio: todos." />
        <Selecao rotulo="Situação" value={status} vazio="Todas" opcoes={opcoes(ROTULOS.statusDaCobranca)}
          onChange={(e) => { setStatus(e.target.value); setPagina(0) }} />
      </div>
      <MensagemDeErro erro={estornar.error ?? cancelar.error ?? tentarDeNovo.error ?? simular.error} />

      {cobrancas.isPending ? <Carregando /> : cobrancas.isError ? <MensagemDeErro erro={cobrancas.error} /> : (
        <>
          <Tabela<Cobranca>
            linhas={p?.itens ?? []}
            chave={(c) => c.id ?? ''}
            vazio="Nenhuma cobrança com esses filtros."
            colunas={[
              { titulo: 'Unidade', valor: (c) => <span className="font-medium">{c.unidade}</span> },
              {
                titulo: 'Descrição',
                valor: (c) => <>{c.descricao}{c.origem !== 'GERACAO_MENSAL' && <> <Etiqueta>{ROTULOS.origemDaCobranca[c.origem as keyof typeof ROTULOS.origemDaCobranca]}</Etiqueta></>}</>,
              },
              { titulo: 'Vencimento', valor: (c) => data(c.vencimento) },
              { titulo: 'Valor', valor: (c) => moeda(c.valor), direita: true },
              {
                titulo: 'Hoje', direita: true,
                valor: (c) => c.status === 'PAGA' ? <span className="text-emerald-700">{moeda(c.valorPago)}</span>
                  : c.status === 'ABERTA' ? <span className={c.vencida ? 'font-semibold text-red-700' : ''}>{moeda(c.valorAtualizado)}</span> : '—',
              },
              {
                titulo: 'Situação',
                valor: (c) => (
                  <Etiqueta cor={c.vencida ? 'vermelho' : COR_DO_STATUS[c.status as keyof typeof COR_DO_STATUS]}>
                    {c.vencida ? `Vencida (${c.diasDeAtraso} d)` : c.status === 'PAGA' ? `Paga ${data(c.pagoEm)}`
                      : ROTULOS.statusDaCobranca[c.status as keyof typeof ROTULOS.statusDaCobranca]}
                  </Etiqueta>
                ),
              },
              {
                titulo: 'Boleto',
                valor: (c) => c.boleto ? (
                  <span>
                    <EtiquetaDoBoleto boleto={c.boleto} />
                    {c.boleto.situacao === 'FALHOU' && <span className="mt-1 block max-w-xs text-xs text-red-700">{c.boleto.erro}</span>}
                  </span>
                ) : '—',
              },
              ...(gerencia ? [{
                titulo: 'Ações', direita: true,
                valor: (c: Cobranca) => (
                  <span className="flex flex-wrap justify-end gap-3">
                    {c.status === 'ABERTA' && c.boleto?.situacao === 'EMITIDO' && (
                      <button className="text-marca-700 underline" onClick={() => setFormulario({ segundaVia: c })}>2ª via</button>
                    )}
                    {c.status === 'ABERTA' && c.boleto?.situacao === 'EMITIDO' && c.boleto.gateway === 'SIMULADO' && (
                      <button className="text-emerald-700 underline" disabled={simular.isPending} onClick={() => simular.mutate(c.id ?? '')}>Simular pagamento</button>
                    )}
                    {c.boleto?.situacao === 'FALHOU' && (
                      <button className="text-marca-700 underline" disabled={tentarDeNovo.isPending} onClick={() => tentarDeNovo.mutate(c.id ?? '')}>Tentar de novo</button>
                    )}
                    {c.status === 'ABERTA' && <button className="text-marca-700 underline" onClick={() => setFormulario({ pagar: c })}>Receber</button>}
                    {c.status === 'ABERTA' && (
                      <button className="text-red-700 underline" onClick={() => {
                        const motivo = window.prompt('Motivo do cancelamento:')
                        if (motivo) cancelar.mutate({ id: c.id ?? '', motivo })
                      }}>Cancelar</button>
                    )}
                    {c.status === 'PAGA' && (
                      <button className="text-red-700 underline" onClick={() => {
                        if (window.confirm('Desfazer o pagamento? O valor sai do caixa.')) estornar.mutate(c.id ?? '')
                      }}>Estornar</button>
                    )}
                  </span>
                ),
              }] : []),
            ]}
          />
          {(p?.totalPaginas ?? 0) > 1 && (
            <div className="flex items-center justify-center gap-3 text-sm">
              <Botao variante="secundario" disabled={pagina === 0} onClick={() => setPagina(pagina - 1)}>Anterior</Botao>
              <span>Página {pagina + 1} de {p?.totalPaginas}</span>
              <Botao variante="secundario" disabled={pagina + 1 >= (p?.totalPaginas ?? 1)} onClick={() => setPagina(pagina + 1)}>Próxima</Botao>
            </div>
          )}
        </>
      )}
    </div>
  )
}

/** Baixa manual. Sem valor, a API usa o valor com multa e juros na data do pagamento. */
function Pagamento({ cobranca: c, aoTerminar }: { cobranca: Cobranca; aoTerminar: () => void }) {
  const cliente = useQueryClient()
  const [dataPagamento, setDataPagamento] = useState(hojeIso())
  const [forma, setForma] = useState('PIX')
  const [valor, setValor] = useState('')
  const [observacao, setObservacao] = useState('')
  const pagar = useMutation({
    mutationFn: async () => dados(await api.POST('/api/cobrancas/{id}/pagamento', {
      params: { path: { id: c.id ?? '' } },
      body: { data: dataPagamento, forma: forma as 'PIX', valorPago: lerReais(valor), observacao: observacao || undefined },
    })),
    onSuccess: async () => {
      await cliente.invalidateQueries({ queryKey: ['cobrancas'] })
      await cliente.invalidateQueries({ queryKey: ['balancete'] })
      aoTerminar()
    },
  })
  return (
    <Cartao titulo={`Receber: ${c.unidade} · ${c.descricao}`}>
      <p className="mb-3 text-sm text-slate-600">
        Valor {moeda(c.valor)}{(c.diasDeAtraso ?? 0) > 0 && <> · hoje, com multa e juros: <strong>{moeda(c.valorAtualizado)}</strong></>}
      </p>
      <form onSubmit={(e) => { e.preventDefault(); pagar.mutate() }} className="grid gap-3 md:grid-cols-2">
        <Campo rotulo="Data do pagamento" type="date" required max={hojeIso()} value={dataPagamento} onChange={(e) => setDataPagamento(e.target.value)} />
        <Selecao rotulo="Forma" value={forma} onChange={(e) => setForma(e.target.value)} opcoes={opcoes(ROTULOS.formaDePagamento)} />
        <Campo rotulo="Valor recebido (R$)" inputMode="decimal" value={valor} onChange={(e) => setValor(e.target.value)}
          ajuda="Vazio: o valor com multa e juros na data. Menor: encargos perdoados." />
        <Campo rotulo="Observação (opcional)" value={observacao} onChange={(e) => setObservacao(e.target.value)} />
        <div className="md:col-span-2"><MensagemDeErro erro={pagar.error} /></div>
        <div className="flex gap-2 md:col-span-2">
          <Botao type="submit" carregando={pagar.isPending}>Registrar pagamento</Botao>
          <Botao type="button" variante="secundario" onClick={aoTerminar}>Cancelar</Botao>
        </div>
      </form>
    </Cartao>
  )
}

function Avulsa({ aoTerminar }: { aoTerminar: () => void }) {
  const cliente = useQueryClient()
  const unidades = useQuery({ queryKey: ['unidades', 'ativas'], queryFn: async () => dados(await api.GET('/api/unidades')) })
  const [unidadeId, setUnidadeId] = useState('')
  const [tipo, setTipo] = useState('MULTA_DISCIPLINAR')
  const [descricao, setDescricao] = useState('')
  const [valor, setValor] = useState('')
  const [vencimento, setVencimento] = useState(hojeIso())
  const emitir = useMutation({
    mutationFn: async () => dados(await api.POST('/api/cobrancas', {
      body: { unidadeId, tipo: tipo as 'OUTRO', descricao, valor: lerReais(valor) ?? 0, vencimento },
    })),
    onSuccess: async () => { await cliente.invalidateQueries({ queryKey: ['cobrancas'] }); aoTerminar() },
  })
  return (
    <Cartao titulo="Cobrança avulsa">
      <form onSubmit={(e: FormEvent) => { e.preventDefault(); emitir.mutate() }} className="grid gap-3 md:grid-cols-2">
        <Selecao rotulo="Unidade" required value={unidadeId} onChange={(e) => setUnidadeId(e.target.value)} vazio="Escolha…"
          opcoes={(unidades.data ?? []).map((u) => ({ valor: u.id ?? '', rotulo: u.descricao ?? '' }))} />
        <Selecao rotulo="Tipo" value={tipo} onChange={(e) => setTipo(e.target.value)} opcoes={opcoes(ROTULOS.itemAvulso)} />
        <Campo rotulo="Descrição" required value={descricao} onChange={(e) => setDescricao(e.target.value)} placeholder="Ex.: Salão de festas 20/12" />
        <Campo rotulo="Valor (R$)" inputMode="decimal" required value={valor} onChange={(e) => setValor(e.target.value)} />
        <Campo rotulo="Vencimento" type="date" required value={vencimento} onChange={(e) => setVencimento(e.target.value)} />
        <div className="md:col-span-2"><MensagemDeErro erro={emitir.error} /></div>
        <div className="flex gap-2 md:col-span-2">
          <Botao type="submit" carregando={emitir.isPending}>Lançar cobrança</Botao>
          <Botao type="button" variante="secundario" onClick={aoTerminar}>Cancelar</Botao>
        </div>
      </form>
    </Cartao>
  )
}

/** Planilha CSV da administradora. Tudo ou nada: com erro, a API diz a linha e o motivo. */
function Importacao({ aoTerminar }: { aoTerminar: () => void }) {
  const cliente = useQueryClient()
  const [arquivo, setArquivo] = useState<File | null>(null)
  const importar = useMutation({
    mutationFn: async () => {
      const formulario = new FormData()
      if (arquivo) formulario.append('arquivo', arquivo)
      const resposta = await requisicaoAutenticada('/api/cobrancas/importacao', { method: 'POST', body: formulario })
      return (await resposta.json()) as components['schemas']['ResultadoDaImportacao']
    },
    onSuccess: () => cliente.invalidateQueries({ queryKey: ['cobrancas'] }),
  })
  return (
    <Cartao titulo="Importar cobranças da administradora">
      <p className="mb-3 text-sm text-slate-600">
        Planilha CSV (salve do Excel como "CSV"), com as colunas:
        <code className="mt-1 block rounded bg-slate-100 p-2 text-xs">unidade;competência;vencimento;valor;situação;data_pagamento;valor_pago;descricao</code>
        Ex.: <code className="text-xs">A-101;10/2026;10/10/2026;1.234,56;PAGA;09/10/2026;1.234,56;Taxa outubro</code>
      </p>
      {importar.data ? (
        <Aviso>Importadas {importar.data.importadas} cobranças ({importar.data.pagas} pagas, {importar.data.abertas} em aberto).</Aviso>
      ) : (
        <form onSubmit={(e) => { e.preventDefault(); importar.mutate() }} className="space-y-3">
          <Campo rotulo="Arquivo" type="file" accept=".csv,text/csv" required onChange={(e) => setArquivo(e.target.files?.[0] ?? null)} />
          <MensagemDeErro erro={importar.error} />
          <div className="flex gap-2">
            <Botao type="submit" carregando={importar.isPending}>Importar</Botao>
            <Botao type="button" variante="secundario" onClick={aoTerminar}>Cancelar</Botao>
          </div>
        </form>
      )}
      {importar.data && <Botao variante="secundario" className="mt-3" onClick={aoTerminar}>Fechar</Botao>}
    </Cartao>
  )
}

function Inadimplencia() {
  const lista = useQuery({ queryKey: ['cobrancas', 'inadimplencia'], queryFn: async () => dados(await api.GET('/api/cobrancas/inadimplencia')) })
  const total = (lista.data ?? []).reduce((soma, i) => soma + (i.valores?.total ?? 0), 0)
  if (lista.isPending) return <Carregando />
  if (lista.isError) return <MensagemDeErro erro={lista.error} />
  return (
    <div className="space-y-3">
      <Cartao>
        <p className="text-sm text-slate-500">Total em atraso hoje (com multa e juros)</p>
        <p className="text-2xl font-bold text-red-700">{moeda(total)}</p>
        <p className="text-sm text-slate-500">{lista.data.length} unidade(s)</p>
      </Cartao>
      <Tabela<Inadimplente>
        linhas={lista.data}
        chave={(i) => i.unidadeId ?? ''}
        vazio="Nenhuma unidade em atraso. 👍"
        colunas={[
          { titulo: 'Unidade', valor: (i) => <span className="font-medium">{i.unidade}</span> },
          { titulo: 'Responsável', valor: (i) => i.responsavel ?? '—' },
          { titulo: 'Cobranças', valor: (i) => i.cobrancasVencidas, direita: true },
          { titulo: 'Atraso', valor: (i) => `${i.diasMaiorAtraso} dias`, direita: true },
          { titulo: 'Valor', valor: (i) => moeda(i.valores?.principal), direita: true },
          { titulo: 'Multa e juros', valor: (i) => moeda((i.valores?.multa ?? 0) + (i.valores?.juros ?? 0)), direita: true },
          { titulo: 'Total hoje', valor: (i) => <strong>{moeda(i.valores?.total)}</strong>, direita: true },
        ]}
      />
    </div>
  )
}
