import { CampoNumero } from '../../componentes/CampoNumerico'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { api, dados } from '../../api/cliente'
import type { components } from '../../api/schema'
import { AreaDeTexto, Selecao } from '../../componentes/formulario'
import { Tabela } from '../../componentes/Tabela'
import { Aviso, Botao, Campo, Cartao, Carregando, Etiqueta, MensagemDeErro, TituloDaPagina } from '../../componentes/ui'
import { useSessao } from '../../sessao/contexto'
import { GESTAO } from '../../sessao/papeis'
import { faixa, opcoes, percentual, ROTULOS, separarIdentificacoes, tipoDeUnidade, vinculo } from '../../util/formato'

type Unidade = components['schemas']['UnidadeResposta']
type Formulario = 'nenhum' | 'nova' | 'lote' | { editar: Unidade }

/** Cadastro de unidades: resumo das frações, lista (tabela no computador), nova, em lote e edição. */
export function Unidades() {
  const { tem } = useSessao()
  const gerencia = tem(...GESTAO)
  const [bloco, setBloco] = useState('')
  const [formulario, setFormulario] = useState<Formulario>('nenhum')

  const resumo = useQuery({ queryKey: ['unidades', 'resumo'], queryFn: async () => dados(await api.GET('/api/unidades/resumo')) })
  const unidades = useQuery({
    queryKey: ['unidades', 'lista', bloco],
    queryFn: async () => dados(await api.GET('/api/unidades', { params: { query: { bloco: bloco || undefined, incluirInativas: true } } })),
  })
  const r = resumo.data

  return (
    <div className="space-y-4">
      <TituloDaPagina subtitulo="Blocos, apartamentos, casas, salas e frações ideais.">Unidades</TituloDaPagina>

      {r && (
        <div className="grid grid-cols-3 gap-3 text-center">
          <Numero rotulo="Ativas" valor={String(r.ativas ?? 0)} />
          <Numero rotulo="Blocos" valor={String(r.blocos?.length ?? 0)} />
          <Numero rotulo="Frações" valor={percentual(r.somaDasFracoes)} alerta={!r.fracoesConferem && (r.ativas ?? 0) > 0} />
        </div>
      )}
      {r && !r.fracoesConferem && (r.ativas ?? 0) > 0 && (
        <Aviso>
          Para o rateio pela fração ideal, as unidades ativas precisam somar 100%
          {(r.semFracaoIdeal ?? 0) > 0 ? ` (${r.semFracaoIdeal} ainda sem fração).` : '.'} Use "Editar" em cada unidade.
        </Aviso>
      )}

      {gerencia && formulario === 'nenhum' && (
        <div className="flex flex-wrap gap-2">
          <Botao onClick={() => setFormulario('nova')}>Nova unidade</Botao>
          <Botao variante="secundario" onClick={() => setFormulario('lote')}>Cadastrar várias</Botao>
        </div>
      )}
      {formulario === 'nova' && <FormularioDeUnidade aoTerminar={() => setFormulario('nenhum')} />}
      {formulario === 'lote' && <FormularioDeLote aoTerminar={() => setFormulario('nenhum')} />}
      {typeof formulario === 'object' && <FormularioDeUnidade unidade={formulario.editar} aoTerminar={() => setFormulario('nenhum')} />}

      {(r?.blocos?.length ?? 0) > 0 && (
        <div className="md:max-w-xs">
          <Selecao rotulo="Bloco" value={bloco} onChange={(e) => setBloco(e.target.value)} vazio="Todos"
            opcoes={(r?.blocos ?? []).map((b) => ({ valor: b, rotulo: b }))} />
        </div>
      )}

      {unidades.isPending ? <Carregando /> : unidades.isError ? <MensagemDeErro erro={unidades.error} /> : (
        <Tabela<Unidade>
          linhas={unidades.data ?? []}
          chave={(u) => u.id ?? ''}
          vazio="Nenhuma unidade cadastrada."
          colunas={[
            { titulo: 'Unidade', valor: (u) => <span className="font-medium">{u.descricao}{!u.ativo && <> <Etiqueta>desativada</Etiqueta></>}</span> },
            { titulo: 'Tipo', valor: (u) => tipoDeUnidade(u.tipo) },
            { titulo: 'Fração', valor: (u) => percentual(u.fracaoIdeal), direita: true },
            {
              titulo: 'Quem está ligado',
              valor: (u) => (u.ocupantes ?? []).length === 0 ? <span className="text-slate-400">ninguém</span>
                : (u.ocupantes ?? []).map((o) => `${o.nome} (${vinculo(o.vinculo).toLowerCase()})`).join(', '),
            },
            ...(gerencia ? [{
              titulo: 'Ações', direita: true,
              valor: (u: Unidade) => <button className="text-marca-700 underline" onClick={() => setFormulario({ editar: u })}>Editar</button>,
            }] : []),
          ]}
        />
      )}
    </div>
  )
}

function Numero({ rotulo, valor, alerta }: { rotulo: string; valor: string; alerta?: boolean }) {
  return (
    <div className="rounded-2xl bg-white p-3 shadow-sm ring-1 ring-slate-200">
      <p className={`text-xl font-bold ${alerta ? 'text-amber-700' : 'text-slate-900'}`}>{valor}</p>
      <p className="text-xs text-slate-500">{rotulo}</p>
    </div>
  )
}

/** Nova unidade ou edição. A fração é digitada em % e enviada à API como número entre 0 e 1. */
function FormularioDeUnidade({ unidade, aoTerminar }: { unidade?: Unidade; aoTerminar: () => void }) {
  const cliente = useQueryClient()
  const [bloco, setBloco] = useState(unidade?.bloco ?? '')
  const [identificacao, setIdentificacao] = useState(unidade?.identificacao ?? '')
  const [tipo, setTipo] = useState<string>(unidade?.tipo ?? 'APARTAMENTO')
  const [fracao, setFracao] = useState(unidade?.fracaoIdeal != null ? String(+(unidade.fracaoIdeal * 100).toFixed(6)) : '')

  const salvar = useMutation({
    mutationFn: async () => {
      const corpo = {
        bloco: bloco || undefined, identificacao, tipo: tipo as Unidade['tipo'] & string,
        fracaoIdeal: fracao ? Number(fracao.replace(',', '.')) / 100 : undefined,
      }
      return unidade?.id
        ? dados(await api.PUT('/api/unidades/{id}', { params: { path: { id: unidade.id } }, body: corpo }))
        : dados(await api.POST('/api/unidades', { body: corpo }))
    },
    onSuccess: async () => { await cliente.invalidateQueries({ queryKey: ['unidades'] }); aoTerminar() },
  })
  const desativar = useMutation({
    mutationFn: async () => dados(await api.POST(unidade?.ativo ? '/api/unidades/{id}/desativacao' : '/api/unidades/{id}/reativacao',
      { params: { path: { id: unidade?.id ?? '' } } })),
    onSuccess: async () => { await cliente.invalidateQueries({ queryKey: ['unidades'] }); aoTerminar() },
  })

  function enviar(e: FormEvent) { e.preventDefault(); salvar.mutate() }

  return (
    <Cartao titulo={unidade ? `Editar ${unidade.descricao}` : 'Nova unidade'}>
      <form onSubmit={enviar} className="grid gap-3 md:grid-cols-2">
        <Campo rotulo="Bloco (opcional)" value={bloco} maxLength={30} onChange={(e) => setBloco(e.target.value)} ajuda="Torre, bloco ou quadra." />
        <Campo rotulo="Identificação" required value={identificacao} maxLength={30} onChange={(e) => setIdentificacao(e.target.value)} ajuda="Ex.: 101, Casa 12, Sala 3." />
        <Selecao rotulo="Tipo" value={tipo} onChange={(e) => setTipo(e.target.value)} opcoes={opcoes(ROTULOS.tipoDeUnidade)} />
        <CampoNumero rotulo="Fração ideal (%)" casas={6} max={100} valor={fracao} aoMudar={(v) => setFracao(v)} ajuda="Ex.: 2,5 para 2,5%. Opcional." />
        <div className="md:col-span-2"><MensagemDeErro erro={salvar.error ?? desativar.error} /></div>
        <div className="flex flex-wrap gap-2 md:col-span-2">
          <Botao type="submit" carregando={salvar.isPending}>Salvar</Botao>
          <Botao type="button" variante="secundario" onClick={aoTerminar}>Cancelar</Botao>
          {unidade && (
            <Botao type="button" variante={unidade.ativo ? 'perigo' : 'secundario'} carregando={desativar.isPending} onClick={() => desativar.mutate()}>
              {unidade.ativo ? 'Desativar' : 'Reativar'}
            </Botao>
          )}
        </div>
      </form>
    </Cartao>
  )
}

function FormularioDeLote({ aoTerminar }: { aoTerminar: () => void }) {
  const cliente = useQueryClient()
  const [bloco, setBloco] = useState('')
  const [tipo, setTipo] = useState('APARTAMENTO')
  const [lista, setLista] = useState('')
  const [de, setDe] = useState('')
  const [ate, setAte] = useState('')
  const identificacoes = separarIdentificacoes(lista)

  const criar = useMutation({
    mutationFn: async () => dados(await api.POST('/api/unidades/lote', {
      body: { bloco: bloco || undefined, tipo: tipo as 'APARTAMENTO', identificacoes },
    })),
    onSuccess: async () => { await cliente.invalidateQueries({ queryKey: ['unidades'] }); aoTerminar() },
  })

  function gerarFaixa() {
    const numeros = faixa(Number(de), Number(ate))
    if (numeros.length > 0) setLista([...identificacoes, ...numeros].join(', '))
  }

  return (
    <Cartao titulo="Cadastrar várias unidades">
      <form onSubmit={(e) => { e.preventDefault(); criar.mutate() }} className="grid gap-3 md:grid-cols-2">
        <Campo rotulo="Bloco (opcional)" value={bloco} onChange={(e) => setBloco(e.target.value)} />
        <Selecao rotulo="Tipo" value={tipo} onChange={(e) => setTipo(e.target.value)} opcoes={opcoes(ROTULOS.tipoDeUnidade)} />
        <div className="flex items-end gap-2 md:col-span-2">
          <CampoNumero rotulo="Sequência: de" valor={de} aoMudar={(v) => setDe(v)} />
          <CampoNumero rotulo="até" valor={ate} aoMudar={(v) => setAte(v)} />
          <Botao type="button" variante="secundario" onClick={gerarFaixa}>Gerar</Botao>
        </div>
        <div className="md:col-span-2">
          <AreaDeTexto rotulo={`Unidades (${identificacoes.length})`} value={lista} onChange={(e) => setLista(e.target.value)}
            ajuda="Separe por vírgula, espaço ou uma por linha. Ex.: 101, 102, 201, 202" />
        </div>
        <div className="md:col-span-2"><MensagemDeErro erro={criar.error} /></div>
        <div className="flex gap-2 md:col-span-2">
          <Botao type="submit" carregando={criar.isPending} disabled={identificacoes.length === 0}>Cadastrar {identificacoes.length || ''}</Botao>
          <Botao type="button" variante="secundario" onClick={aoTerminar}>Cancelar</Botao>
        </div>
        <p className="text-xs text-slate-500 md:col-span-2">Tudo ou nada: se alguma já existir, nenhuma é criada.</p>
      </form>
    </Cartao>
  )
}
