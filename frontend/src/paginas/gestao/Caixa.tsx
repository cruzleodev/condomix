import { useAdesao } from '../../api/adesao'
import { CampoDinheiro } from '../../componentes/CampoNumerico'
import { CampoData } from '../../componentes/CampoData'
import { CampoMes } from '../../componentes/CampoMes'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api, dados } from '../../api/cliente'
import type { components } from '../../api/schema'
import { Selecao } from '../../componentes/formulario'
import { Tabela } from '../../componentes/Tabela'
import { Botao, Campo, Cartao, Carregando, Etiqueta, MensagemDeErro, TituloDaPagina, Vazio } from '../../componentes/ui'
import { useSessao } from '../../sessao/contexto'
import { GESTAO } from '../../sessao/papeis'
import { data, hojeIso, lerReais, mesAtual, moeda, opcoes, periodoDoMes, ROTULOS } from '../../util/formato'

type Conta = components['schemas']['ContaResposta']
type Lancamento = components['schemas']['LancamentoResposta']
type Formulario = 'nenhum' | 'conta' | 'despesa' | 'receita' | 'transferencia' | 'categoria'

/** Contas com saldo, lançamentos (despesa, receita, transferência) e o extrato do mês. */
export function Caixa() {
  const { tem } = useSessao()
  const gerencia = tem(...GESTAO)
  const [formulario, setFormulario] = useState<Formulario>('nenhum')
  const contas = useQuery({ queryKey: ['caixa', 'contas'], queryFn: async () => dados(await api.GET('/api/financeiro/contas')) })
  const lista = contas.data ?? []
  const total = lista.filter((c) => c.ativa).reduce((s, c) => s + (c.saldoAtual ?? 0), 0)
  const fechar = () => setFormulario('nenhum')

  return (
    <div className="space-y-4">
      <TituloDaPagina subtitulo="Os pagamentos das cobranças entram sozinhos na conta de recebimento.">Caixa</TituloDaPagina>
      {contas.isPending ? <Carregando /> : contas.isError ? <MensagemDeErro erro={contas.error} /> : (
        <>
          <div className="grid gap-3 md:grid-cols-3">
            <Cartao className="md:col-span-3">
              <p className="text-sm text-slate-500">Saldo total hoje</p>
              <p className="text-2xl font-bold">{moeda(total)}</p>
            </Cartao>
            {lista.map((c) => (
              <Cartao key={c.id}>
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="truncate font-medium">{c.nome}</p>
                    <p className="text-xs text-slate-500">{ROTULOS.tipoDeContaExibido[c.tipo as keyof typeof ROTULOS.tipoDeContaExibido] ?? c.tipo}{c.banco && ` · ${c.banco}`}</p>
                  </div>
                  {c.recebimentoPadrao && <Etiqueta cor="marca">recebimentos</Etiqueta>}
                  {!c.ativa && <Etiqueta>desativada</Etiqueta>}
                </div>
                <p className="mt-2 text-xl font-semibold">{moeda(c.saldoAtual)}</p>
              </Cartao>
            ))}
          </div>
          {lista.length === 0 && <Vazio>Nenhuma conta ainda. Cadastre a conta do condomínio (ou ela é criada no primeiro pagamento).</Vazio>}
        </>
      )}

      {gerencia && formulario === 'nenhum' && (
        <div className="flex flex-wrap gap-2">
          <Botao onClick={() => setFormulario('despesa')} disabled={lista.length === 0}>Lançar despesa</Botao>
          <Botao variante="secundario" onClick={() => setFormulario('receita')} disabled={lista.length === 0}>Lançar receita</Botao>
          <Botao variante="secundario" onClick={() => setFormulario('transferencia')} disabled={lista.length < 2}>Transferir</Botao>
          <Botao variante="secundario" onClick={() => setFormulario('conta')}>Nova conta</Botao>
          <Botao variante="secundario" onClick={() => setFormulario('categoria')}>Nova categoria</Botao>
        </div>
      )}
      {formulario === 'conta' && <NovaConta aoTerminar={fechar} />}
      {(formulario === 'despesa' || formulario === 'receita') && <NovoLancamento tipo={formulario === 'despesa' ? 'DESPESA' : 'RECEITA'} contas={lista} aoTerminar={fechar} />}
      {formulario === 'transferencia' && <Transferencia contas={lista} aoTerminar={fechar} />}
      {formulario === 'categoria' && <NovaCategoria aoTerminar={fechar} />}

      <Extrato contas={lista} gerencia={gerencia} />
    </div>
  )
}

function useAtualizarCaixa() {
  const cliente = useQueryClient()
  return async () => {
    await cliente.invalidateQueries({ queryKey: ['caixa'] })
    await cliente.invalidateQueries({ queryKey: ['balancete'] })
  }
}

function NovaConta({ aoTerminar }: { aoTerminar: () => void }) {
  const atualizar = useAtualizarCaixa()
  const { inicioDoHistorico } = useAdesao()
  const [nome, setNome] = useState('')
  const [tipo, setTipo] = useState('CONTA_CORRENTE')
  const [banco, setBanco] = useState('')
  const [saldo, setSaldo] = useState('0')
  const [desde, setDesde] = useState(`${mesAtual()}-01`)
  const criar = useMutation({
    mutationFn: async () => dados(await api.POST('/api/financeiro/contas', {
      body: { nome, tipo: tipo as 'CONTA_CORRENTE', banco: banco || undefined, saldoInicial: lerReais(saldo) ?? 0, dataSaldoInicial: desde },
    })),
    onSuccess: async () => { await atualizar(); aoTerminar() },
  })
  return (
    <Cartao titulo="Nova conta">
      <form onSubmit={(e) => { e.preventDefault(); criar.mutate() }} className="grid gap-3 md:grid-cols-2">
        <Campo rotulo="Nome" required value={nome} onChange={(e) => setNome(e.target.value)} placeholder="Ex.: Conta corrente Banco X" />
        <Selecao rotulo="Tipo" value={tipo} onChange={(e) => setTipo(e.target.value)} opcoes={opcoes(ROTULOS.tipoDeConta)} />
        <Campo rotulo="Banco (opcional)" value={banco} onChange={(e) => setBanco(e.target.value)} />
        <CampoDinheiro rotulo="Saldo inicial (R$)" required valor={saldo} aoMudar={(v) => setSaldo(v)} />
        <CampoData rotulo="Saldo na data de" required valor={desde} aoMudar={(v) => setDesde(v)} min={inicioDoHistorico}
          ajuda="Lançamentos só a partir desta data." />
        <div className="md:col-span-2"><MensagemDeErro erro={criar.error} /></div>
        <div className="flex gap-2 md:col-span-2">
          <Botao type="submit" carregando={criar.isPending}>Criar conta</Botao>
          <Botao type="button" variante="secundario" onClick={aoTerminar}>Cancelar</Botao>
        </div>
      </form>
    </Cartao>
  )
}

function NovoLancamento({ tipo, contas, aoTerminar }: { tipo: 'DESPESA' | 'RECEITA'; contas: Conta[]; aoTerminar: () => void }) {
  const atualizar = useAtualizarCaixa()
  const { inicioDoHistorico } = useAdesao()
  const categorias = useQuery({ queryKey: ['caixa', 'categorias'], queryFn: async () => dados(await api.GET('/api/financeiro/categorias')) })
  const ativas = contas.filter((c) => c.ativa)
  const [contaId, setContaId] = useState(ativas.find((c) => c.recebimentoPadrao)?.id ?? ativas[0]?.id ?? '')
  const [categoriaId, setCategoriaId] = useState('')
  const [quando, setQuando] = useState(hojeIso())
  const [descricao, setDescricao] = useState('')
  const [valor, setValor] = useState('')
  const [favorecido, setFavorecido] = useState('')
  const lancar = useMutation({
    mutationFn: async () => dados(await api.POST('/api/financeiro/lancamentos', {
      body: { tipo, contaId, categoriaId, data: quando, descricao, valor: lerReais(valor) ?? 0, favorecido: favorecido || undefined },
    })),
    onSuccess: async () => { await atualizar(); aoTerminar() },
  })
  const natureza = tipo === 'DESPESA' ? 'DESPESA' : 'RECEITA'
  return (
    <Cartao titulo={tipo === 'DESPESA' ? 'Lançar despesa' : 'Lançar receita'}>
      <form onSubmit={(e) => { e.preventDefault(); lancar.mutate() }} className="grid gap-3 md:grid-cols-2">
        <Selecao rotulo="Categoria" required value={categoriaId} onChange={(e) => setCategoriaId(e.target.value)} vazio="Escolha…"
          opcoes={(categorias.data ?? []).filter((c) => c.natureza === natureza && c.ativa).map((c) => ({ valor: c.id ?? '', rotulo: c.nome ?? '' }))} />
        <Selecao rotulo="Conta" required value={contaId} onChange={(e) => setContaId(e.target.value)}
          opcoes={ativas.map((c) => ({ valor: c.id ?? '', rotulo: c.nome ?? '' }))} />
        <Campo rotulo="Descrição" required value={descricao} onChange={(e) => setDescricao(e.target.value)} placeholder={tipo === 'DESPESA' ? 'Ex.: Conta de luz de outubro' : 'Ex.: Venda de sucata'} />
        <CampoDinheiro rotulo="Valor (R$)" required valor={valor} aoMudar={(v) => setValor(v)} />
        <CampoData rotulo="Data" required valor={quando} aoMudar={(v) => setQuando(v)} min={inicioDoHistorico} />
        <Campo rotulo={tipo === 'DESPESA' ? 'Fornecedor (opcional)' : 'Pagador (opcional)'} value={favorecido} onChange={(e) => setFavorecido(e.target.value)} />
        <div className="md:col-span-2"><MensagemDeErro erro={lancar.error} /></div>
        <div className="flex gap-2 md:col-span-2">
          <Botao type="submit" carregando={lancar.isPending}>Lançar</Botao>
          <Botao type="button" variante="secundario" onClick={aoTerminar}>Cancelar</Botao>
        </div>
      </form>
    </Cartao>
  )
}

function Transferencia({ contas, aoTerminar }: { contas: Conta[]; aoTerminar: () => void }) {
  const atualizar = useAtualizarCaixa()
  const { inicioDoHistorico } = useAdesao()
  const ativas = contas.filter((c) => c.ativa)
  const [origem, setOrigem] = useState(ativas[0]?.id ?? '')
  const [destino, setDestino] = useState(ativas[1]?.id ?? '')
  const [valor, setValor] = useState('')
  const [quando, setQuando] = useState(hojeIso())
  const transferir = useMutation({
    mutationFn: async () => dados(await api.POST('/api/financeiro/transferencias', {
      body: { contaOrigemId: origem, contaDestinoId: destino, valor: lerReais(valor) ?? 0, data: quando },
    })),
    onSuccess: async () => { await atualizar(); aoTerminar() },
  })
  const opcoesDeConta = ativas.map((c) => ({ valor: c.id ?? '', rotulo: `${c.nome} (${moeda(c.saldoAtual)})` }))
  return (
    <Cartao titulo="Transferir entre contas">
      <form onSubmit={(e) => { e.preventDefault(); transferir.mutate() }} className="grid gap-3 md:grid-cols-2">
        <Selecao rotulo="De" value={origem} onChange={(e) => setOrigem(e.target.value)} opcoes={opcoesDeConta} />
        <Selecao rotulo="Para" value={destino} onChange={(e) => setDestino(e.target.value)} opcoes={opcoesDeConta} />
        <CampoDinheiro rotulo="Valor (R$)" required valor={valor} aoMudar={(v) => setValor(v)} />
        <CampoData rotulo="Data" required valor={quando} aoMudar={(v) => setQuando(v)} min={inicioDoHistorico} />
        <p className="text-xs text-slate-500 md:col-span-2">Transferência não é receita nem despesa: o saldo total não muda.</p>
        <div className="md:col-span-2"><MensagemDeErro erro={transferir.error} /></div>
        <div className="flex gap-2 md:col-span-2">
          <Botao type="submit" carregando={transferir.isPending}>Transferir</Botao>
          <Botao type="button" variante="secundario" onClick={aoTerminar}>Cancelar</Botao>
        </div>
      </form>
    </Cartao>
  )
}

function NovaCategoria({ aoTerminar }: { aoTerminar: () => void }) {
  const atualizar = useAtualizarCaixa()
  const [nome, setNome] = useState('')
  const [natureza, setNatureza] = useState('DESPESA')
  const criar = useMutation({
    mutationFn: async () => dados(await api.POST('/api/financeiro/categorias', { body: { nome, natureza: natureza as 'DESPESA' } })),
    onSuccess: async () => { await atualizar(); aoTerminar() },
  })
  return (
    <Cartao titulo="Nova categoria">
      <form onSubmit={(e) => { e.preventDefault(); criar.mutate() }} className="grid gap-3 md:grid-cols-2">
        <Campo rotulo="Nome" required value={nome} onChange={(e) => setNome(e.target.value)} placeholder="Ex.: Jardinagem" />
        <Selecao rotulo="Tipo" value={natureza} onChange={(e) => setNatureza(e.target.value)}
          opcoes={[{ valor: 'DESPESA', rotulo: 'Despesa' }, { valor: 'RECEITA', rotulo: 'Receita' }]} />
        <div className="md:col-span-2"><MensagemDeErro erro={criar.error} /></div>
        <div className="flex gap-2 md:col-span-2">
          <Botao type="submit" carregando={criar.isPending}>Criar</Botao>
          <Botao type="button" variante="secundario" onClick={aoTerminar}>Cancelar</Botao>
        </div>
      </form>
    </Cartao>
  )
}

function Extrato({ contas, gerencia }: { contas: Conta[]; gerencia: boolean }) {
  const atualizar = useAtualizarCaixa()
  const { inicioDoHistorico } = useAdesao()
  const [mes, setMes] = useState(mesAtual())
  const [contaId, setContaId] = useState('')
  const [de, ate] = periodoDoMes(mes)
  const extrato = useQuery({
    queryKey: ['caixa', 'extrato', mes, contaId],
    queryFn: async () => dados(await api.GET('/api/financeiro/lancamentos', { params: { query: { de, ate, contaId: contaId || undefined } } })),
  })
  const cancelar = useMutation({
    mutationFn: async ({ id, motivo }: { id: string; motivo: string }) =>
      dados(await api.POST('/api/financeiro/lancamentos/{id}/cancelamento', { params: { path: { id } }, body: { motivo } })),
    onSuccess: atualizar,
  })
  return (
    <Cartao titulo="Extrato">
      <div className="mb-3 grid items-start gap-3 sm:grid-cols-[18rem_18rem]">
        <CampoMes rotulo="Mês" valor={mes} aoMudar={setMes} min={inicioDoHistorico?.slice(0, 7)} />
        <Selecao rotulo="Conta" value={contaId} onChange={(e) => setContaId(e.target.value)} vazio="Todas"
          opcoes={contas.map((c) => ({ valor: c.id ?? '', rotulo: c.nome ?? '' }))} />
      </div>
      <MensagemDeErro erro={cancelar.error} />
      {extrato.isPending ? <Carregando /> : extrato.isError ? <MensagemDeErro erro={extrato.error} /> : (
        <Tabela<Lancamento>
          linhas={extrato.data}
          chave={(l) => l.id ?? ''}
          vazio="Nenhum lançamento neste mês."
          colunas={[
            { titulo: 'Descrição', valor: (l) => <span className={l.cancelado ? 'text-slate-400 line-through' : 'font-medium'}>{l.descricao}</span> },
            { titulo: 'Data', valor: (l) => data(l.data) },
            { titulo: 'Categoria', valor: (l) => l.categoria ?? ROTULOS.tipoDeLancamento[l.tipo as keyof typeof ROTULOS.tipoDeLancamento] },
            { titulo: 'Conta', valor: (l) => l.conta },
            {
              titulo: 'Valor', direita: true,
              valor: (l) => {
                const sai = l.tipo === 'DESPESA' || l.tipo === 'TRANSFERENCIA_SAIDA'
                return <span className={l.cancelado ? 'text-slate-400 line-through' : sai ? 'text-red-700' : 'text-emerald-700'}>{sai ? '−' : '+'} {moeda(l.valor)}</span>
              },
            },
            {
              titulo: 'Situação', direita: true,
              valor: (l) => l.cancelado ? <Etiqueta>cancelado</Etiqueta>
                : l.origem === 'COBRANCA' ? <Etiqueta cor="marca">cobrança</Etiqueta>
                : gerencia ? (
                  <button className="text-red-700 underline" onClick={() => {
                    const motivo = window.prompt('Motivo do cancelamento:')
                    if (motivo) cancelar.mutate({ id: l.id ?? '', motivo })
                  }}>Cancelar</button>
                ) : null,
            },
          ]}
        />
      )}
    </Cartao>
  )
}
