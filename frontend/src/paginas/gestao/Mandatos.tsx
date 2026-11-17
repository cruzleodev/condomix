import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { api, dados } from '../../api/cliente'
import type { Mandato } from '../../api/tipos'
import { Selecao } from '../../componentes/formulario'
import { Tabela } from '../../componentes/Tabela'
import { Aviso, Botao, Campo, Cartao, Carregando, Etiqueta, MensagemDeErro, TituloDaPagina, Vazio } from '../../componentes/ui'
import { useSessao } from '../../sessao/contexto'
import { GESTAO } from '../../sessao/papeis'
import { data, hojeIso, opcoes, ROTULOS, umAnoDepoisMenosUmDia } from '../../util/formato'

const COR_DA_SITUACAO = { VIGENTE: 'verde', A_VENCER: 'amarelo', PRORROGADO: 'vermelho', FUTURO: 'marca', ENCERRADO: 'cinza' } as const

/** Síndico, subsíndico e conselho: quem ocupa hoje, histórico, eleição e encerramento. */
export function Mandatos() {
  const { tem } = useSessao()
  const gerencia = tem(...GESTAO)
  const [registrando, setRegistrando] = useState(false)
  const [encerrando, setEncerrando] = useState<Mandato | null>(null)

  const mandatos = useQuery({ queryKey: ['mandatos', 'todos'], queryFn: async () => dados(await api.GET('/api/mandatos')) })
  const todos = mandatos.data ?? []
  const atuais = todos.filter((m) => ['VIGENTE', 'A_VENCER', 'PRORROGADO', 'FUTURO'].includes(m.situacao ?? ''))
  const encerrados = todos.filter((m) => m.situacao === 'ENCERRADO')

  return (
    <div className="space-y-4">
      <TituloDaPagina subtitulo="O sistema registra o que a assembleia decidiu. O mandato vencido continua valendo até a posse do sucessor.">
        Cargos e mandatos
      </TituloDaPagina>
      {gerencia && !registrando && !encerrando && <Botao onClick={() => setRegistrando(true)}>Registrar eleição</Botao>}
      {registrando && <RegistroDeEleicao aoTerminar={() => setRegistrando(false)} />}
      {encerrando && <Encerramento mandato={encerrando} aoTerminar={() => setEncerrando(null)} />}

      {mandatos.isPending ? <Carregando /> : mandatos.isError ? <MensagemDeErro erro={mandatos.error} /> : (
        <>
          <section>
            <h2 className="mb-2 text-sm font-semibold uppercase text-slate-500">Atuais</h2>
            {atuais.length === 0 ? <Vazio>Nenhum mandato em andamento.</Vazio> : (
              <ul className="grid gap-3 md:grid-cols-2">
                {atuais.map((m) => (
                  <li key={m.id}>
                    <Cartao>
                      <div className="flex items-start justify-between gap-2">
                        <div>
                          <p className="text-sm text-slate-500">{m.cargoRotulo}</p>
                          <p className="font-semibold">{m.ocupante?.nome}</p>
                          <p className="text-sm text-slate-600">{data(m.inicio)} a {data(m.fimPrevisto)}</p>
                          {m.beneficio && <p className="text-sm text-slate-600">Desconto de {m.beneficio.descontoPercentual}% na taxa</p>}
                        </div>
                        <Etiqueta cor={COR_DA_SITUACAO[m.situacao as keyof typeof COR_DA_SITUACAO] ?? 'cinza'}>
                          {ROTULOS.situacaoDoMandato[m.situacao as keyof typeof ROTULOS.situacaoDoMandato] ?? m.situacao}
                        </Etiqueta>
                      </div>
                      {m.aviso && <div className="mt-3"><Aviso>{m.aviso}</Aviso></div>}
                      {gerencia && m.situacao !== 'FUTURO' && (
                        <button className="mt-3 text-sm text-red-700 underline" onClick={() => { setRegistrando(false); setEncerrando(m) }}>
                          Encerrar antes do prazo
                        </button>
                      )}
                    </Cartao>
                  </li>
                ))}
              </ul>
            )}
          </section>
          {encerrados.length > 0 && (
            <section>
              <h2 className="mb-2 text-sm font-semibold uppercase text-slate-500">Histórico</h2>
              <Tabela<Mandato>
                linhas={encerrados}
                chave={(m) => m.id ?? ''}
                colunas={[
                  { titulo: 'Cargo', valor: (m) => <span className="font-medium">{m.cargoRotulo}: {m.ocupante?.nome}</span> },
                  { titulo: 'Período', valor: (m) => `${data(m.inicio)} a ${data(m.encerradoEm)}` },
                  { titulo: 'Motivo', valor: (m) => m.motivoEncerramento === 'SUCESSAO' ? 'Sucessão'
                    : ROTULOS.motivoDeEncerramento[m.motivoEncerramento as keyof typeof ROTULOS.motivoDeEncerramento] ?? '' },
                ]}
              />
            </section>
          )}
        </>
      )}
    </div>
  )
}

function RegistroDeEleicao({ aoTerminar }: { aoTerminar: () => void }) {
  const cliente = useQueryClient()
  const pessoas = useQuery({ queryKey: ['pessoas', ''], queryFn: async () => dados(await api.GET('/api/pessoas')) })
  const unidades = useQuery({ queryKey: ['unidades', 'ativas'], queryFn: async () => dados(await api.GET('/api/unidades')) })
  const [pessoaId, setPessoaId] = useState('')
  const [cargo, setCargo] = useState('SINDICO')
  const [inicio, setInicio] = useState(hojeIso())
  const [fim, setFim] = useState(umAnoDepoisMenosUmDia(hojeIso()))
  const [desconto, setDesconto] = useState('')
  const [unidadeId, setUnidadeId] = useState('')

  const registrar = useMutation({
    mutationFn: async () => dados(await api.POST('/api/mandatos', {
      body: {
        pessoaId, cargo: cargo as 'SINDICO', inicio, fimPrevisto: fim,
        descontoPercentual: desconto ? Number(desconto.replace(',', '.')) : undefined,
        unidadeBeneficiadaId: desconto ? unidadeId || undefined : undefined,
      },
    })),
    onSuccess: async () => {
      await cliente.invalidateQueries({ queryKey: ['mandatos'] })
      aoTerminar()
    },
  })

  function enviar(e: FormEvent) { e.preventDefault(); registrar.mutate() }

  return (
    <Cartao titulo="Registrar eleição">
      <form onSubmit={enviar} className="grid gap-3 md:grid-cols-2">
        <Selecao rotulo="Pessoa eleita" required value={pessoaId} onChange={(e) => setPessoaId(e.target.value)} vazio="Escolha…"
          opcoes={(pessoas.data ?? []).map((p) => ({ valor: p.id ?? '', rotulo: p.nome ?? '' }))} />
        <Selecao rotulo="Cargo" value={cargo} onChange={(e) => setCargo(e.target.value)} opcoes={opcoes(ROTULOS.cargo)} />
        <Campo rotulo="Posse" type="date" required value={inicio}
          onChange={(e) => { setInicio(e.target.value); if (e.target.value) setFim(umAnoDepoisMenosUmDia(e.target.value)) }} />
        <Campo rotulo="Fim do mandato" type="date" required value={fim} onChange={(e) => setFim(e.target.value)} ajuda="No máximo 2 anos." />
        <Campo rotulo="Desconto na taxa (%) — opcional" inputMode="decimal" value={desconto} onChange={(e) => setDesconto(e.target.value)}
          ajuda="100 para isenção." />
        {desconto && (
          <Selecao rotulo="Unidade com o desconto" required value={unidadeId} onChange={(e) => setUnidadeId(e.target.value)} vazio="Escolha…"
            opcoes={(unidades.data ?? []).map((u) => ({ valor: u.id ?? '', rotulo: u.descricao ?? '' }))} />
        )}
        <p className="text-xs text-slate-500 md:col-span-2">
          Síndico e subsíndico: o mandato atual termina sozinho no dia anterior à posse do eleito.
        </p>
        <div className="md:col-span-2"><MensagemDeErro erro={registrar.error} /></div>
        <div className="flex gap-2 md:col-span-2">
          <Botao type="submit" carregando={registrar.isPending}>Registrar</Botao>
          <Botao type="button" variante="secundario" onClick={aoTerminar}>Cancelar</Botao>
        </div>
      </form>
    </Cartao>
  )
}

function Encerramento({ mandato, aoTerminar }: { mandato: Mandato; aoTerminar: () => void }) {
  const cliente = useQueryClient()
  const [ultimoDia, setUltimoDia] = useState(hojeIso())
  const [motivo, setMotivo] = useState('RENUNCIA')
  const encerrar = useMutation({
    mutationFn: async () => dados(await api.POST('/api/mandatos/{id}/encerramento', {
      params: { path: { id: mandato.id ?? '' } },
      body: { ultimoDia, motivo: motivo as 'RENUNCIA' },
    })),
    onSuccess: async () => { await cliente.invalidateQueries({ queryKey: ['mandatos'] }); aoTerminar() },
  })
  return (
    <Cartao titulo={`Encerrar mandato de ${mandato.ocupante?.nome} (${mandato.cargoRotulo})`}>
      <form onSubmit={(e) => { e.preventDefault(); encerrar.mutate() }} className="grid gap-3 md:grid-cols-2">
        <Campo rotulo="Último dia no cargo" type="date" required value={ultimoDia} onChange={(e) => setUltimoDia(e.target.value)} />
        <Selecao rotulo="Motivo" value={motivo} onChange={(e) => setMotivo(e.target.value)} opcoes={opcoes(ROTULOS.motivoDeEncerramento)} />
        <div className="md:col-span-2"><MensagemDeErro erro={encerrar.error} /></div>
        <div className="flex gap-2 md:col-span-2">
          <Botao type="submit" variante="perigo" carregando={encerrar.isPending}>Encerrar mandato</Botao>
          <Botao type="button" variante="secundario" onClick={aoTerminar}>Cancelar</Botao>
        </div>
      </form>
    </Cartao>
  )
}
