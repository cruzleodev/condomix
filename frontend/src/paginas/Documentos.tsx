import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { api, dados, requisicaoAutenticada } from '../api/cliente'
import type { Documento } from '../api/tipos'
import { Selecao } from '../componentes/formulario'
import { Botao, Campo, Cartao, Carregando, Etiqueta, MensagemDeErro, TituloDaPagina, Vazio } from '../componentes/ui'
import { abrirArquivo } from '../nativo/app'
import { useSessao } from '../sessao/contexto'
import { GESTAO } from '../sessao/papeis'
import { data, tamanhoDeArquivo } from '../util/formato'

/** Convenção, regimento, atas... Busca em português (sem diferenciar acentos) e filtro por categoria. */
export function Documentos() {
  const { tem } = useSessao()
  const [busca, setBusca] = useState('')
  const [termo, setTermo] = useState('')
  const [semTermo, setSemTermo] = useState(false)
  const [categoria, setCategoria] = useState<string>('')
  const [publicando, setPublicando] = useState(false)

  const categorias = useQuery({ queryKey: ['documentos', 'categorias'], queryFn: async () => dados(await api.GET('/api/documentos/categorias')) })
  const documentos = useQuery({
    queryKey: ['documentos', termo, categoria],
    queryFn: async () => dados(await api.GET('/api/documentos', {
      params: { query: { busca: termo || undefined, categoria: (categoria || undefined) as never, tamanho: 50 } },
    })),
  })

  return (
    <div>
      <TituloDaPagina subtitulo="Convenção, regimento, atas, leis e comunicados.">Documentos</TituloDaPagina>
      {tem(...GESTAO) && (
        publicando ? <FormularioDePublicacao aoTerminar={() => setPublicando(false)} categorias={categorias.data ?? []} />
          : <Botao className="mb-4 w-full md:w-auto" onClick={() => setPublicando(true)}>Publicar documento</Botao>
      )}
      <form className="mb-3 flex gap-2 md:max-w-xl" onSubmit={(e) => {
        e.preventDefault()
        // Sem nada escrito: se já havia uma busca, volta a mostrar tudo; senão, explica o que fazer.
        setSemTermo(!busca.trim() && !termo)
        setTermo(busca.trim())
      }}>
        <input
          type="search" placeholder="Buscar (ex.: convenção, piscina)" value={busca}
          onChange={(e) => { setBusca(e.target.value); setSemTermo(false) }}
          aria-describedby={semTermo ? 'busca-vazia' : undefined}
          className="min-h-11 flex-1 rounded-xl border border-slate-300 bg-white px-3 outline-none focus:border-marca-600"
          aria-label="Buscar documentos"
        />
        <Botao type="submit" variante="secundario">Buscar</Botao>
      </form>
      {semTermo && (
        <p id="busca-vazia" role="status" className="-mt-1 mb-3 text-sm text-amber-800">
          Digite uma palavra para buscar, por exemplo: convenção, piscina ou assembleia.
        </p>
      )}
      {termo && (
        <p className="-mt-1 mb-3 text-sm text-slate-600">
          Resultados para "{termo}".{' '}
          <button type="button" className="text-marca-700 underline" onClick={() => { setBusca(''); setTermo('') }}>Limpar a busca</button>
        </p>
      )}
      {/* No celular, uma lista (ocupa uma linha); no computador, onde sobra espaço, as etiquetas. */}
      <div className="mb-4 md:hidden">
        <Selecao rotulo="Categoria" value={categoria} onChange={(e) => setCategoria(e.target.value)} vazio="Todas"
          opcoes={(categorias.data ?? []).map((c) => ({ valor: c.codigo ?? '', rotulo: c.rotulo ?? '' }))} />
      </div>
      <div className="mb-4 hidden flex-wrap gap-2 md:flex">
        <Filtro ativo={categoria === ''} onClick={() => setCategoria('')}>Todas</Filtro>
        {(categorias.data ?? []).map((c) => (
          <Filtro key={c.codigo} ativo={categoria === c.codigo} onClick={() => setCategoria(c.codigo ?? '')}>{c.rotulo}</Filtro>
        ))}
      </div>
      {documentos.isPending ? <Carregando /> : documentos.isError ? <MensagemDeErro erro={documentos.error} /> :
        (documentos.data?.itens ?? []).length === 0 ? <Vazio>{termo ? `Nada encontrado para "${termo}".` : 'Nenhum documento publicado.'}</Vazio> : (
          <ul className="grid gap-3 md:grid-cols-2">{(documentos.data?.itens ?? []).map((d) => <ItemDeDocumento key={d.id} documento={d} />)}</ul>
        )}
    </div>
  )
}

function Filtro({ ativo, onClick, children }: { ativo: boolean; onClick: () => void; children: string | undefined }) {
  return (
    <button onClick={onClick}
      className={`shrink-0 rounded-full px-3 py-1.5 text-sm ${ativo ? 'bg-marca-700 text-white' : 'bg-white text-slate-700 ring-1 ring-slate-300'}`}>
      {children}
    </button>
  )
}

function ItemDeDocumento({ documento: d }: { documento: Documento }) {
  const [erro, setErro] = useState<unknown>(null)
  const [abrindo, setAbrindo] = useState(false)

  /** O arquivo exige o token, então é baixado pelo app e aberto a partir da memória. */
  async function abrir() {
    setErro(null)
    setAbrindo(true)
    try {
      const resposta = await requisicaoAutenticada(`/api/documentos/${d.id}/arquivo`)
      await abrirArquivo(await resposta.blob(), d.arquivo?.nome ?? 'documento')
    } catch (e) {
      setErro(e)
    } finally {
      setAbrindo(false)
    }
  }

  return (
    <li>
      <Cartao>
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="font-medium">{d.titulo}</p>
            <p className="mt-1"><Etiqueta>{d.categoriaRotulo}</Etiqueta></p>
            {d.descricao && <p className="mt-2 text-sm text-slate-600">{d.descricao}</p>}
            <p className="mt-2 text-xs text-slate-500">
              {d.dataReferencia ? `Referente a ${data(d.dataReferencia)} · ` : ''}{d.arquivo?.nome} {tamanhoDeArquivo(d.arquivo?.tamanho)}
            </p>
          </div>
          <Botao variante="secundario" onClick={abrir} carregando={abrindo} className="shrink-0">Abrir</Botao>
        </div>
        <MensagemDeErro erro={erro} />
      </Cartao>
    </li>
  )
}

function FormularioDePublicacao({ aoTerminar, categorias }:
  { aoTerminar: () => void; categorias: { codigo?: string; rotulo?: string }[] }) {
  const cliente = useQueryClient()
  const [titulo, setTitulo] = useState('')
  const [categoria, setCategoria] = useState('ATA_ASSEMBLEIA')
  const [descricao, setDescricao] = useState('')
  const [arquivo, setArquivo] = useState<File | null>(null)

  const publicar = useMutation({
    mutationFn: async () => {
      const formulario = new FormData()
      formulario.append('titulo', titulo)
      formulario.append('categoria', categoria)
      if (descricao) formulario.append('descricao', descricao)
      if (arquivo) formulario.append('arquivo', arquivo)
      await requisicaoAutenticada('/api/documentos', { method: 'POST', body: formulario })
    },
    onSuccess: async () => {
      await cliente.invalidateQueries({ queryKey: ['documentos'] })
      aoTerminar()
    },
  })

  function enviar(e: FormEvent) {
    e.preventDefault()
    publicar.mutate()
  }

  return (
    <Cartao titulo="Publicar documento" className="mb-4">
      <form onSubmit={enviar} className="space-y-3">
        <Campo rotulo="Título" required maxLength={200} value={titulo} onChange={(e) => setTitulo(e.target.value)} />
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-slate-700">Categoria</span>
          <select value={categoria} onChange={(e) => setCategoria(e.target.value)}
            className="min-h-11 w-full rounded-xl border border-slate-300 bg-white px-3">
            {categorias.map((c) => <option key={c.codigo} value={c.codigo}>{c.rotulo}</option>)}
          </select>
        </label>
        <Campo rotulo="Descrição (opcional)" value={descricao} onChange={(e) => setDescricao(e.target.value)} />
        <Campo rotulo="Arquivo" type="file" required accept=".pdf,.jpg,.jpeg,.png,.docx,.xlsx"
          ajuda="PDF, JPG, PNG, DOCX ou XLSX, até 20 MB." onChange={(e) => setArquivo(e.target.files?.[0] ?? null)} />
        <MensagemDeErro erro={publicar.error} />
        <div className="flex gap-2">
          <Botao type="submit" carregando={publicar.isPending} className="flex-1">Publicar</Botao>
          <Botao type="button" variante="secundario" onClick={aoTerminar}>Cancelar</Botao>
        </div>
      </form>
    </Cartao>
  )
}
