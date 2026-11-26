import { CampoDinheiro, CampoNumero } from '../../componentes/CampoNumerico'
import { CampoData } from '../../componentes/CampoData'
import { CampoMes } from '../../componentes/CampoMes'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { api, dados } from '../../api/cliente'
import type { components } from '../../api/schema'
import { Selecao } from '../../componentes/formulario'
import { Tabela } from '../../componentes/Tabela'
import { Aviso, Botao, Campo, Cartao, Carregando, Etiqueta, MensagemDeErro, TituloDaPagina } from '../../componentes/ui'
import { useSessao } from '../../sessao/contexto'
import { GESTAO } from '../../sessao/papeis'
import { competencia, data, lerReais, mesAtual, moeda, opcoes, proximoMes, ROTULOS } from '../../util/formato'

type Configuracao = components['schemas']['ConfiguracaoResposta']
type Resultado = components['schemas']['ResultadoDaGeracao']
type Linha = components['schemas']['Linha']
type TaxaExtra = { descricao: string; valor: string; rateio: string }

/** Como a taxa é cobrada, a simulação do mês e a geração das cobranças de todas as unidades. */
export function TaxaDoMes() {
  const { tem } = useSessao()
  const gerencia = tem(...GESTAO)
  const configuracao = useQuery({ queryKey: ['financeiro', 'configuracao'], queryFn: async () => dados(await api.GET('/api/financeiro/configuracao')) })
  return (
    <div className="space-y-4">
      <TituloDaPagina subtitulo="Simule antes de gerar: nada é gravado até você confirmar.">Taxa do mês</TituloDaPagina>
      {configuracao.isPending ? <Carregando /> : configuracao.isError ? <MensagemDeErro erro={configuracao.error} /> : (
        <>
          <div className="grid gap-4 md:grid-cols-2">
            <ComoCobra configuracao={configuracao.data} gerencia={gerencia} />
            {configuracao.data.tipoRateio === 'VALOR_POR_GRUPO' && <Grupos gerencia={gerencia} />}
          </div>
          {gerencia && <Geracao />}
          <Historico gerencia={gerencia} />
        </>
      )}
    </div>
  )
}

function ComoCobra({ configuracao: c, gerencia }: { configuracao: Configuracao; gerencia: boolean }) {
  const cliente = useQueryClient()
  const [editando, setEditando] = useState(false)
  const [tipo, setTipo] = useState<string>(c.tipoRateio ?? 'FRACAO_IDEAL')
  const [dia, setDia] = useState(String(c.diaVencimento ?? 10))
  const [multa, setMulta] = useState(String(c.multaPercentual ?? 2))
  const [juros, setJuros] = useState(String(c.jurosMensalPercentual ?? 1))
  const [fundo, setFundo] = useState(String(c.fundoReservaPercentual ?? 0))
  const salvar = useMutation({
    mutationFn: async () => dados(await api.PUT('/api/financeiro/configuracao', {
      body: {
        tipoRateio: tipo as 'IGUALITARIO', diaVencimento: Number(dia),
        multaPercentual: lerReais(multa) ?? 0, jurosMensalPercentual: lerReais(juros) ?? 0, fundoReservaPercentual: lerReais(fundo) ?? 0,
      },
    })),
    onSuccess: async () => { await cliente.invalidateQueries({ queryKey: ['financeiro'] }); setEditando(false) },
  })

  if (!editando) {
    return (
      <Cartao titulo="Como a taxa é cobrada" acao={gerencia && <button className="text-sm text-marca-700 underline" onClick={() => setEditando(true)}>Alterar</button>}>
        <dl className="grid grid-cols-2 gap-3 text-sm">
          <div className="col-span-2"><dt className="text-slate-500">Rateio</dt><dd className="font-medium">{c.tipoRateioRotulo}</dd></div>
          <div><dt className="text-slate-500">Vencimento</dt><dd>todo dia {c.diaVencimento}</dd></div>
          <div><dt className="text-slate-500">Fundo de reserva</dt><dd>{c.fundoReservaPercentual}% da taxa</dd></div>
          <div><dt className="text-slate-500">Multa por atraso</dt><dd>{c.multaPercentual}%</dd></div>
          <div><dt className="text-slate-500">Juros</dt><dd>{c.jurosMensalPercentual}% ao mês</dd></div>
        </dl>
      </Cartao>
    )
  }
  return (
    <Cartao titulo="Como a taxa é cobrada">
      <form onSubmit={(e) => { e.preventDefault(); salvar.mutate() }} className="grid gap-3 md:grid-cols-2">
        <div className="md:col-span-2">
          <Selecao rotulo="Rateio" value={tipo} onChange={(e) => setTipo(e.target.value)} opcoes={opcoes(ROTULOS.tipoDeRateio)} />
        </div>
        <CampoNumero rotulo="Dia do vencimento" min={1} max={28} required valor={dia} aoMudar={(v) => setDia(v)} ajuda="De 1 a 28." />
        <CampoNumero rotulo="Fundo de reserva (%)" casas={2} max={100} valor={fundo} aoMudar={(v) => setFundo(v)} ajuda="Sobre a taxa ordinária." />
        <CampoNumero rotulo="Multa por atraso (%)" casas={2} max={2} valor={multa} aoMudar={(v) => setMulta(v)} ajuda="Máximo de 2% (Código Civil)." />
        <CampoNumero rotulo="Juros ao mês (%)" casas={2} valor={juros} aoMudar={(v) => setJuros(v)} />
        <div className="md:col-span-2"><MensagemDeErro erro={salvar.error} /></div>
        <div className="flex gap-2 md:col-span-2">
          <Botao type="submit" carregando={salvar.isPending}>Salvar</Botao>
          <Botao type="button" variante="secundario" onClick={() => setEditando(false)}>Cancelar</Botao>
        </div>
      </form>
    </Cartao>
  )
}

/** Rateio por grupo: cada grupo tem um valor e as suas unidades (marcadas na lista). */
function Grupos({ gerencia }: { gerencia: boolean }) {
  const cliente = useQueryClient()
  const grupos = useQuery({ queryKey: ['financeiro', 'grupos'], queryFn: async () => dados(await api.GET('/api/financeiro/grupos')) })
  const unidades = useQuery({ queryKey: ['unidades', 'ativas'], queryFn: async () => dados(await api.GET('/api/unidades')) })
  const [nome, setNome] = useState('')
  const [valor, setValor] = useState('')
  const [editando, setEditando] = useState<string | null>(null)
  const [marcadas, setMarcadas] = useState<string[]>([])

  const criar = useMutation({
    mutationFn: async () => dados(await api.POST('/api/financeiro/grupos', { body: { nome, valorMensal: lerReais(valor) ?? 0 } })),
    onSuccess: async () => { await cliente.invalidateQueries({ queryKey: ['financeiro', 'grupos'] }); setNome(''); setValor('') },
  })
  const definir = useMutation({
    mutationFn: async (id: string) => dados(await api.PUT('/api/financeiro/grupos/{id}/unidades', { params: { path: { id } }, body: { unidades: marcadas } })),
    onSuccess: async () => { await cliente.invalidateQueries({ queryKey: ['financeiro', 'grupos'] }); setEditando(null) },
  })
  const nomeDa = (id: string) => (unidades.data ?? []).find((u) => u.id === id)?.descricao ?? '?'

  return (
    <Cartao titulo="Grupos e valores">
      <ul className="space-y-3">
        {(grupos.data ?? []).map((g) => (
          <li key={g.id} className="rounded-xl bg-slate-50 p-3 text-sm">
            <div className="flex justify-between gap-2">
              <span className="font-medium">{g.nome}</span><span className="font-semibold">{moeda(g.valorMensal)}</span>
            </div>
            {editando === g.id ? (
              <div className="mt-2">
                <div className="flex max-h-48 flex-wrap gap-2 overflow-y-auto">
                  {(unidades.data ?? []).map((u) => (
                    <label key={u.id} className="flex items-center gap-1 rounded-lg bg-white px-2 py-1 ring-1 ring-slate-200">
                      <input type="checkbox" className="accent-marca-700" checked={marcadas.includes(u.id ?? '')}
                        onChange={(e) => setMarcadas(e.target.checked ? [...marcadas, u.id ?? ''] : marcadas.filter((m) => m !== u.id))} />
                      {u.descricao}
                    </label>
                  ))}
                </div>
                <MensagemDeErro erro={definir.error} />
                <div className="mt-2 flex gap-2">
                  <Botao carregando={definir.isPending} onClick={() => definir.mutate(g.id ?? '')}>Salvar unidades</Botao>
                  <Botao variante="secundario" onClick={() => setEditando(null)}>Cancelar</Botao>
                </div>
              </div>
            ) : (
              <p className="mt-1 text-slate-600">
                {(g.unidades ?? []).length === 0 ? 'Nenhuma unidade' : (g.unidades ?? []).map(nomeDa).join(', ')}
                {gerencia && <> · <button className="text-marca-700 underline" onClick={() => { setMarcadas(g.unidades ?? []); setEditando(g.id ?? null) }}>escolher unidades</button></>}
              </p>
            )}
          </li>
        ))}
      </ul>
      {gerencia && (
        <form onSubmit={(e) => { e.preventDefault(); criar.mutate() }} className="mt-3 flex flex-wrap items-end gap-2">
          <div className="flex-1"><Campo rotulo="Novo grupo" placeholder="Ex.: Cobertura" required value={nome} onChange={(e) => setNome(e.target.value)} /></div>
          <div className="w-32"><CampoDinheiro rotulo="Valor (R$)" required valor={valor} aoMudar={(v) => setValor(v)} /></div>
          <Botao type="submit" variante="secundario" carregando={criar.isPending}>Criar</Botao>
          <div className="w-full"><MensagemDeErro erro={criar.error} /></div>
        </form>
      )}
    </Cartao>
  )
}

/** Simula e, se estiver certo, gera. O resultado mostra o valor de cada unidade, item por item. */
function Geracao() {
  const cliente = useQueryClient()
  const [mes, setMes] = useState(proximoMes(mesAtual()))
  const [valor, setValor] = useState('')
  const [vencimento, setVencimento] = useState('')
  const [extras, setExtras] = useState<TaxaExtra[]>([])
  const [simulacao, setSimulacao] = useState<Resultado | null>(null)
  const [gerado, setGerado] = useState<Resultado | null>(null)

  const corpo = () => ({
    competencia: mes, valorOrdinariaTotal: lerReais(valor), vencimento: vencimento || undefined,
    taxasExtras: extras.filter((x) => x.descricao && lerReais(x.valor)).map((x) => ({
      descricao: x.descricao, valorTotal: lerReais(x.valor) ?? 0, rateio: x.rateio as 'FRACAO_IDEAL',
    })),
  })
  const simular = useMutation({
    mutationFn: async () => dados(await api.POST('/api/financeiro/geracoes/simulacao', { body: corpo() })),
    onSuccess: (r) => { setSimulacao(r); setGerado(null) },
  })
  const gerar = useMutation({
    mutationFn: async () => dados(await api.POST('/api/financeiro/geracoes', { body: corpo() })),
    onSuccess: async (r) => {
      setGerado(r); setSimulacao(null)
      await cliente.invalidateQueries({ queryKey: ['financeiro', 'geracoes'] })
      await cliente.invalidateQueries({ queryKey: ['cobrancas'] })
    },
  })
  const mudou = () => setSimulacao(null)

  function enviar(e: FormEvent) { e.preventDefault(); simular.mutate() }

  return (
    <Cartao titulo="Gerar a taxa de um mês">
      <form onSubmit={enviar} className="grid gap-3 md:grid-cols-3">
        <CampoMes rotulo="Mês" valor={mes} aoMudar={(m) => { setMes(m); mudou() }} />
        <CampoDinheiro rotulo="Valor total da taxa (R$)" valor={valor} aoMudar={(v) => { setValor(v); mudou() }}
          ajuda="Total a dividir. No rateio por grupo, deixe vazio." />
        <CampoData rotulo="Vencimento (opcional)" valor={vencimento} aoMudar={(v) => { setVencimento(v); mudou() }}
          ajuda="Vazio: o dia configurado." />
        <div className="space-y-2 md:col-span-3">
          {extras.map((x, i) => (
            <div key={i} className="grid gap-2 rounded-xl bg-slate-50 p-2 md:grid-cols-[2fr_1fr_1fr_auto] md:items-end">
              <Campo rotulo="Taxa extra" placeholder="Ex.: Pintura da fachada" value={x.descricao}
                onChange={(e) => { setExtras(extras.map((y, j) => j === i ? { ...y, descricao: e.target.value } : y)); mudou() }} />
              <CampoDinheiro rotulo="Total (R$)" valor={x.valor}
                aoMudar={(v) => { setExtras(extras.map((y, j) => j === i ? { ...y, valor: v } : y)); mudou() }} />
              <Selecao rotulo="Divisão" value={x.rateio} opcoes={[{ valor: 'FRACAO_IDEAL', rotulo: 'Fração ideal' }, { valor: 'IGUALITARIO', rotulo: 'Igual' }]}
                onChange={(e) => { setExtras(extras.map((y, j) => j === i ? { ...y, rateio: e.target.value } : y)); mudou() }} />
              <Botao type="button" variante="perigo" onClick={() => { setExtras(extras.filter((_, j) => j !== i)); mudou() }}>Remover</Botao>
            </div>
          ))}
          <button type="button" className="text-sm text-marca-700 underline"
            onClick={() => setExtras([...extras, { descricao: '', valor: '', rateio: 'FRACAO_IDEAL' }])}>+ Taxa extra (obra, rateio)</button>
        </div>
        <div className="md:col-span-3"><MensagemDeErro erro={simular.error ?? gerar.error} /></div>
        <div className="md:col-span-3"><Botao type="submit" carregando={simular.isPending}>Simular</Botao></div>
      </form>

      {gerado && <div className="mt-4"><Aviso>Taxa de {competencia(gerado.competencia)} gerada: {gerado.cobrancas?.length} cobranças, total de {moeda(gerado.totalGeral)}. Veja em Cobranças.</Aviso></div>}
      {simulacao && <ResultadoDaSimulacao resultado={simulacao} confirmar={() => gerar.mutate()} gerando={gerar.isPending} />}
    </Cartao>
  )
}

function ResultadoDaSimulacao({ resultado: r, confirmar, gerando }: { resultado: Resultado; confirmar: () => void; gerando: boolean }) {
  return (
    <div className="mt-4 space-y-3 border-t border-slate-100 pt-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <p className="font-semibold">Simulação de {competencia(r.competencia)} · vence em {data(r.vencimento)}</p>
        <p className="text-lg font-bold">{moeda(r.totalGeral)}</p>
      </div>
      {(r.avisos ?? []).map((a) => <Aviso key={a}>{a}</Aviso>)}
      {(r.isentas ?? []).length > 0 && <p className="text-sm text-slate-600">Sem cobrança (isentas): {(r.isentas ?? []).join(', ')}</p>}
      <Tabela<Linha>
        linhas={r.cobrancas ?? []}
        chave={(l) => l.unidadeId ?? ''}
        colunas={[
          { titulo: 'Unidade', valor: (l) => <span className="font-medium">{l.unidade}</span> },
          { titulo: 'Responsável', valor: (l) => l.responsavel ?? <span className="text-amber-700">ninguém</span> },
          { titulo: 'Composição', valor: (l) => (l.itens ?? []).map((i) => `${i.descricao} ${moeda(i.valor)}`).join(' · ') },
          { titulo: 'Total', valor: (l) => <strong>{moeda(l.total)}</strong>, direita: true },
        ]}
      />
      <Botao onClick={confirmar} carregando={gerando} className="w-full md:w-auto">Confirmar e gerar {r.cobrancas?.length} cobranças</Botao>
    </div>
  )
}

function Historico({ gerencia }: { gerencia: boolean }) {
  const cliente = useQueryClient()
  const geracoes = useQuery({ queryKey: ['financeiro', 'geracoes'], queryFn: async () => dados(await api.GET('/api/financeiro/geracoes')) })
  const cancelar = useMutation({
    mutationFn: async (id: string) => dados(await api.POST('/api/financeiro/geracoes/{id}/cancelamento', {
      params: { path: { id } }, body: { motivo: 'Cancelada pelo síndico no app' },
    })),
    onSuccess: async () => {
      await cliente.invalidateQueries({ queryKey: ['financeiro', 'geracoes'] })
      await cliente.invalidateQueries({ queryKey: ['cobrancas'] })
    },
  })
  if ((geracoes.data ?? []).length === 0) return null
  return (
    <Cartao titulo="Gerações feitas">
      <MensagemDeErro erro={cancelar.error} />
      <ul className="divide-y divide-slate-100 text-sm">
        {(geracoes.data ?? []).map((g) => (
          <li key={g.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
            <span>
              <strong>{competencia(g.competencia)}</strong> · {ROTULOS.tipoDeRateio[g.tipoRateio as keyof typeof ROTULOS.tipoDeRateio]}
              {g.valorOrdinariaTotal != null && ` · ${moeda(g.valorOrdinariaTotal)}`}
            </span>
            {g.canceladaEm ? <Etiqueta>cancelada</Etiqueta> : gerencia && (
              <button className="text-red-700 underline" disabled={cancelar.isPending}
                onClick={() => { if (window.confirm(`Cancelar a taxa de ${competencia(g.competencia)} e todas as cobranças dela?`)) cancelar.mutate(g.id ?? '') }}>
                Cancelar geração
              </button>
            )}
          </li>
        ))}
      </ul>
    </Cartao>
  )
}
