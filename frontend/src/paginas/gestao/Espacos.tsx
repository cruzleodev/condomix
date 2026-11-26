import { BotaoDeIcone } from '../../componentes/BotaoDeIcone'
import { BotaoComIcone } from '../../componentes/BotaoComIcone'
import { CampoDinheiro, CampoNumero } from '../../componentes/CampoNumerico'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { api, dados } from '../../api/cliente'
import type { Espaco, Reserva } from '../../api/tipos'
import { CancelamentoDeReserva, EtiquetaDaReserva } from '../../componentes/Reservas'
import { Selecao } from '../../componentes/formulario'
import { Tabela } from '../../componentes/Tabela'
import { Botao, Campo, Cartao, Carregando, Etiqueta, MensagemDeErro, TituloDaPagina } from '../../componentes/ui'
import { useSessao } from '../../sessao/contexto'
import { GESTAO } from '../../sessao/papeis'
import { diaDaSemana, hojeIso, horario, lerReais, moeda, somarDias } from '../../util/formato'

/** Os períodos que podem ser reservados, com as horas sugeridas ao escolher (ajustáveis). */
const PERIODOS = [
  { nome: 'Dia inteiro', inicio: '08:00', fim: '22:00' },
  { nome: 'Manhã', inicio: '08:00', fim: '12:00' },
  { nome: 'Almoço', inicio: '11:00', fim: '15:00' },
  { nome: 'Tarde', inicio: '13:00', fim: '18:00' },
  { nome: 'Noite', inicio: '18:00', fim: '23:00' },
]

/**
 * As opções da lista: os períodos ainda não usados neste espaço. Um nome antigo, digitado antes da lista existir,
 * continua aparecendo, para não sumir ao editar.
 */
function opcoesDePeriodo(atual: string, usados: string[]) {
  const nomes = PERIODOS.map((p) => p.nome).filter((n) => n === atual || !usados.includes(n))
  if (atual && !nomes.includes(atual)) nomes.unshift(atual)
  return nomes.map((n) => ({ valor: n, rotulo: n }))
}

interface HorarioEditado {
  nome: string
  inicio: string
  fim: string
}

/** Os espaços comuns (cadastro, horários, aluguel e regras) e as reservas do condomínio. */
export function Espacos() {
  const { tem } = useSessao()
  const gestao = tem(...GESTAO)
  const [editando, setEditando] = useState<Espaco | 'novo' | null>(null)
  const espacos = useQuery({ queryKey: ['reservas', 'espacos'], queryFn: async () => dados(await api.GET('/api/espacos')) })
  return (
    <div className="space-y-6">
      <TituloDaPagina subtitulo="Os espaços comuns que os moradores reservam pelo app.">Espaços e reservas</TituloDaPagina>
      {editando ? <FormularioDeEspaco espaco={editando === 'novo' ? null : editando} aoTerminar={() => setEditando(null)} />
        : gestao && <Botao className="w-full md:w-auto" onClick={() => setEditando('novo')}>Cadastrar espaço</Botao>}
      <section>
        <h2 className="mb-2 text-sm font-semibold uppercase text-slate-500">Espaços</h2>
        {espacos.isPending ? <Carregando /> : espacos.isError ? <MensagemDeErro erro={espacos.error} /> : (
          <Tabela<Espaco>
            linhas={espacos.data}
            chave={(e) => e.id ?? ''}
            vazio="Nenhum espaço cadastrado. Cadastre o salão de festas, a churrasqueira…"
            colunas={[
              { titulo: 'Espaço', valor: (e) => <span className="font-medium">{e.nome}</span> },
              { titulo: 'Horários', valor: (e) => (e.horarios ?? []).map((h) => horario(h.nome, h.inicio, h.fim)).join(' · ') },
              { titulo: 'Aluguel', valor: (e) => (e.valorAluguel ?? 0) > 0 ? moeda(e.valorAluguel) : 'Sem aluguel', direita: true },
              { titulo: 'Situação', valor: (e) => e.ativo ? <Etiqueta cor="verde">Aceita reservas</Etiqueta> : <Etiqueta cor="cinza">Desativado</Etiqueta> },
              { titulo: 'Ações', valor: (e) => gestao ? <AcoesDoEspaco espaco={e} aoEditar={() => setEditando(e)} /> : null, direita: true },
            ]}
          />
        )}
      </section>
      <ReservasDoCondominio gestao={gestao} />
    </div>
  )
}

function AcoesDoEspaco({ espaco: e, aoEditar }: { espaco: Espaco; aoEditar: () => void }) {
  const cache = useQueryClient()
  const alternar = useMutation({
    mutationFn: async () => {
      const caminho = { params: { path: { id: e.id ?? '' } } }
      dados(e.ativo ? await api.POST('/api/espacos/{id}/desativacao', caminho) : await api.POST('/api/espacos/{id}/reativacao', caminho))
    },
    onSuccess: () => cache.invalidateQueries({ queryKey: ['reservas'] }),
  })
  return (
    <span className="inline-flex flex-wrap justify-end md:justify-center gap-1 text-sm md:flex-nowrap">
      <BotaoDeIcone icone="editar" rotulo="Editar o espaço" onClick={aoEditar} />
      <BotaoDeIcone icone={e.ativo ? 'desativar' : 'reativar'} rotulo={e.ativo ? 'Desativar o espaço' : 'Reativar o espaço'}
        perigo={e.ativo} disabled={alternar.isPending} onClick={() => {
          if (!e.ativo || window.confirm(`Desativar ${e.nome}? Ele deixa de aceitar reservas; as já feitas continuam valendo.`)) alternar.mutate()
        }} />
      <MensagemDeErro erro={alternar.error} />
    </span>
  )
}

const PADRAO: HorarioEditado[] = [{ nome: 'Dia inteiro', inicio: '08:00', fim: '22:00' }]

function FormularioDeEspaco({ espaco, aoTerminar }: { espaco: Espaco | null; aoTerminar: () => void }) {
  const cache = useQueryClient()
  const r = espaco?.regras
  const [nome, setNome] = useState(espaco?.nome ?? '')
  const [descricao, setDescricao] = useState(espaco?.descricao ?? '')
  const [capacidade, setCapacidade] = useState(espaco?.capacidade ? String(espaco.capacidade) : '')
  const [aluguel, setAluguel] = useState(espaco?.valorAluguel ? String(espaco.valorAluguel).replace('.', ',') : '')
  const [minima, setMinima] = useState(String(r?.antecedenciaMinimaDias ?? 0))
  const [maxima, setMaxima] = useState(String(r?.antecedenciaMaximaDias ?? 90))
  const [limite, setLimite] = useState(r?.limitePorMes ? String(r.limitePorMes) : '')
  const [diasParaCancelar, setDiasParaCancelar] = useState(String(r?.diasParaCancelar ?? 0))
  const [bloqueia, setBloqueia] = useState(r?.bloqueiaInadimplente ?? true)
  const [horarios, setHorarios] = useState<HorarioEditado[]>(espaco?.horarios?.length
    ? espaco.horarios.map((h) => ({ nome: h.nome ?? '', inicio: (h.inicio ?? '').slice(0, 5), fim: (h.fim ?? '').slice(0, 5) }))
    : PADRAO)
  const salvar = useMutation({
    mutationFn: async () => {
      const body = {
        nome, descricao: descricao || undefined, capacidade: capacidade ? Number(capacidade) : undefined,
        valorAluguel: lerReais(aluguel) ?? 0, antecedenciaMinimaDias: Number(minima || 0), antecedenciaMaximaDias: Number(maxima || 0),
        limitePorMes: limite ? Number(limite) : undefined, bloqueiaInadimplente: bloqueia, diasParaCancelar: Number(diasParaCancelar || 0),
        horarios,
      }
      return dados(espaco
        ? await api.PUT('/api/espacos/{id}', { params: { path: { id: espaco.id ?? '' } }, body })
        : await api.POST('/api/espacos', { body }))
    },
    onSuccess: () => {
      cache.invalidateQueries({ queryKey: ['reservas'] })
      aoTerminar()
    },
  })
  const mudar = (i: number, campo: keyof HorarioEditado, valor: string) =>
    setHorarios(horarios.map((h, j) => (j === i ? { ...h, [campo]: valor } : h)))
  /** Escolher o período sugere as horas, sem apagar horas que a gestão já ajustou à mão. */
  function escolherPeriodo(i: number, nome: string) {
    setHorarios(horarios.map((h, j) => {
      if (j !== i) return h
      const anterior = PERIODOS.find((p) => p.nome === h.nome)
      const sugerido = PERIODOS.find((p) => p.nome === nome)
      const horasDoAnterior = (!h.inicio && !h.fim) || (anterior && h.inicio === anterior.inicio && h.fim === anterior.fim)
      return sugerido && horasDoAnterior ? { nome, inicio: sugerido.inicio, fim: sugerido.fim } : { ...h, nome }
    }))
  }
  return (
    <Cartao titulo={espaco ? `Editar ${espaco.nome}` : 'Cadastrar espaço'}>
      <form className="space-y-4" onSubmit={(e: FormEvent) => { e.preventDefault(); salvar.mutate() }}>
        <div className="grid gap-3 md:grid-cols-2">
          <Campo rotulo="Nome" required maxLength={100} value={nome} onChange={(e) => setNome(e.target.value)} placeholder="Salão de festas" />
          <CampoDinheiro rotulo="Aluguel por reserva (R$)" valor={aluguel} aoMudar={(v) => setAluguel(v)}
            ajuda="Vazio ou zero: sem aluguel. Com valor, a reserva vira cobrança da unidade." />
          <Campo rotulo="Descrição (opcional)" value={descricao} onChange={(e) => setDescricao(e.target.value)} maxLength={2000} />
          <CampoNumero rotulo="Capacidade (pessoas, opcional)" min={1} valor={capacidade} aoMudar={(v) => setCapacidade(v)} />
        </div>
        <fieldset className="space-y-2">
          <legend className="mb-1 text-sm font-semibold text-slate-700">Horários que podem ser reservados</legend>
          <p className="text-xs text-slate-500">
            Ex.: um só "Dia inteiro", ou "Almoço" e "Noite". Ao escolher, as horas vêm sugeridas e podem ser ajustadas. Os horários
            não podem se sobrepor.
          </p>
          {horarios.map((h, i) => (
            <div key={i} className="grid grid-cols-[1fr_auto_auto] items-end gap-2 md:grid-cols-[2fr_1fr_1fr_auto]">
              <div className="col-span-3 md:col-span-1">
                <Selecao rotulo={`Horário ${i + 1}`} required value={h.nome} vazio="Escolha…"
                  onChange={(e) => escolherPeriodo(i, e.target.value)}
                  opcoes={opcoesDePeriodo(h.nome, horarios.filter((_, j) => j !== i).map((o) => o.nome))} />
              </div>
              <Campo rotulo="Das" type="time" required value={h.inicio} onChange={(e) => mudar(i, 'inicio', e.target.value)} />
              <Campo rotulo="Às" type="time" required value={h.fim} onChange={(e) => mudar(i, 'fim', e.target.value)}
                ref={(campo) => campo?.setCustomValidity(h.inicio && h.fim && h.fim <= h.inicio ? 'O fim precisa ser depois do início.' : '')} />
              <BotaoDeIcone icone="remover" rotulo="Tirar este horário" perigo disabled={horarios.length === 1}
                onClick={() => setHorarios(horarios.filter((_, j) => j !== i))} />
            </div>
          ))}
          <BotaoComIcone icone="adicionar" onClick={() => setHorarios([...horarios, { nome: '', inicio: '', fim: '' }])}>Outro horário</BotaoComIcone>
        </fieldset>
        <fieldset className="space-y-3">
          <legend className="mb-1 text-sm font-semibold text-slate-700">Regras</legend>
          <div className="grid gap-3 md:grid-cols-2">
            <CampoNumero rotulo="Antecedência mínima (dias)" min={0} valor={minima} aoMudar={(v) => setMinima(v)}
              ajuda="0: pode reservar para o mesmo dia." />
            <CampoNumero rotulo="Reservar até quantos dias à frente" min={0} valor={maxima} aoMudar={(v) => setMaxima(v)} />
            <CampoNumero rotulo="Limite por mês, por unidade" min={1} valor={limite} aoMudar={(v) => setLimite(v)}
              ajuda="Vazio: sem limite." />
            <CampoNumero rotulo="Cancelar sem custo até quantos dias antes" min={0} valor={diasParaCancelar}
              aoMudar={(v) => setDiasParaCancelar(v)} ajuda="Depois disso, o aluguel continua devido. 0: até o próprio dia." />
          </div>
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={bloqueia} onChange={(e) => setBloqueia(e.target.checked)} className="h-5 w-5 accent-marca-700" />
            Unidade com cobrança vencida não reserva
          </label>
        </fieldset>
        <MensagemDeErro erro={salvar.error} />
        <div className="flex flex-col gap-2 md:flex-row">
          <Botao type="submit" carregando={salvar.isPending}>Salvar</Botao>
          <Botao type="button" variante="secundario" onClick={aoTerminar}>Cancelar</Botao>
        </div>
      </form>
    </Cartao>
  )
}

/** As reservas dos próximos 60 dias (e as dos últimos 7), com o cancelamento pela gestão. */
function ReservasDoCondominio({ gestao }: { gestao: boolean }) {
  const hoje = hojeIso()
  const [cancelando, setCancelando] = useState<Reserva | null>(null)
  const reservas = useQuery({
    queryKey: ['reservas', 'condominio', hoje],
    queryFn: async () => dados(await api.GET('/api/reservas', { params: { query: { de: somarDias(hoje, -7), ate: somarDias(hoje, 60) } } })),
  })
  return (
    <section className="space-y-3">
      <h2 className="text-sm font-semibold uppercase text-slate-500">Reservas dos próximos 60 dias</h2>
      {reservas.isPending ? <Carregando /> : reservas.isError ? <MensagemDeErro erro={reservas.error} /> : (
        <Tabela<Reserva>
          linhas={reservas.data}
          chave={(r) => r.id ?? ''}
          vazio="Nenhuma reserva."
          colunas={[
            { titulo: 'Dia', valor: (r) => <span className="font-medium capitalize">{diaDaSemana(r.data)}</span> },
            { titulo: 'Horário', valor: (r) => horario(r.horario, r.inicio, r.fim) },
            { titulo: 'Espaço', valor: (r) => <Link to={`/reservas/${r.espacoId}`} className="text-marca-700 underline">{r.espaco}</Link> },
            { titulo: 'Unidade', valor: (r) => r.unidade },
            { titulo: 'Aluguel', valor: (r) => (r.valor ?? 0) > 0 ? moeda(r.valor) : '—', direita: true },
            { titulo: 'Situação', valor: (r) => <EtiquetaDaReserva reserva={r} /> },
            {
              titulo: 'Ações', direita: true,
              valor: (r) => gestao && r.situacao === 'CONFIRMADA' && !r.aconteceu
                ? <BotaoDeIcone icone="cancelar" rotulo="Cancelar a reserva" perigo onClick={() => setCancelando(r)} /> : null,
            },
          ]}
        />
      )}
      {cancelando && (
        <Cartao titulo={`Cancelar: ${cancelando.espaco}, ${diaDaSemana(cancelando.data)}, unidade ${cancelando.unidade}`}
          acao={<BotaoDeIcone icone="fechar" rotulo="Fechar" onClick={() => setCancelando(null)} />}>
          <CancelamentoDeReserva key={cancelando.id} reserva={cancelando} gestao abertoDeInicio />
        </Cartao>
      )}
    </section>
  )
}
