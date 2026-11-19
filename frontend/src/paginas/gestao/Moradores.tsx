import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { api, dados } from '../../api/cliente'
import type { components } from '../../api/schema'
import { LinkDeConvite, Selecao } from '../../componentes/formulario'
import { Tabela } from '../../componentes/Tabela'
import { Botao, Campo, Cartao, Carregando, Etiqueta, MensagemDeErro, TituloDaPagina } from '../../componentes/ui'
import { useSessao } from '../../sessao/contexto'
import { GESTAO } from '../../sessao/papeis'
import { documento, hojeIso, opcoes, ROTULOS, telefone, vinculo } from '../../util/formato'

type Pessoa = components['schemas']['PessoaResposta']
type Convite = components['schemas']['ConviteGerado']

/** Pessoas do condomínio: busca, unidades de cada uma, cadastro de morador e convites de acesso. */
export function Moradores() {
  const { tem } = useSessao()
  const gerencia = tem(...GESTAO)
  const [busca, setBusca] = useState('')
  const [termo, setTermo] = useState('')
  const [cadastrando, setCadastrando] = useState(false)
  const [convidando, setConvidando] = useState<Pessoa | null>(null)
  const [editando, setEditando] = useState<Pessoa | null>(null)
  const [convite, setConvite] = useState<Convite | null>(null)

  const pessoas = useQuery({
    queryKey: ['pessoas', termo],
    queryFn: async () => dados(await api.GET('/api/pessoas', { params: { query: { busca: termo || undefined } } })),
  })

  return (
    <div className="space-y-4">
      <TituloDaPagina subtitulo="Proprietários, inquilinos e moradores, e o acesso de cada um ao app.">Moradores</TituloDaPagina>

      {convite && <LinkDeConvite link={convite.link ?? ''} email={convite.email} enviadoPorEmail={convite.enviadoPorEmail} />}
      {gerencia && !cadastrando && !convidando && !editando && <Botao onClick={() => { setConvite(null); setCadastrando(true) }}>Cadastrar morador</Botao>}
      {cadastrando && <CadastroDeMorador aoTerminar={(c) => { setCadastrando(false); setConvite(c) }} />}
      {editando && <EdicaoDePessoa pessoa={editando} aoTerminar={() => setEditando(null)} />}
      {convidando && <Convidar pessoa={convidando} aoTerminar={(c) => { setConvidando(null); setConvite(c) }} />}

      <form className="flex gap-2 md:max-w-xl" onSubmit={(e) => { e.preventDefault(); setTermo(busca.trim()) }}>
        <input type="search" aria-label="Buscar pessoas" placeholder="Nome ou CPF/CNPJ" value={busca} onChange={(e) => setBusca(e.target.value)}
          className="min-h-11 flex-1 rounded-xl border border-slate-300 bg-white px-3 outline-none focus:border-marca-600" />
        <Botao type="submit" variante="secundario">Buscar</Botao>
      </form>

      {pessoas.isPending ? <Carregando /> : pessoas.isError ? <MensagemDeErro erro={pessoas.error} /> : (
        <Tabela<Pessoa>
          linhas={pessoas.data ?? []}
          chave={(p) => p.id ?? ''}
          vazio={termo ? `Ninguém encontrado para "${termo}".` : 'Nenhuma pessoa cadastrada.'}
          colunas={[
            {
              titulo: 'Nome',
              valor: (p) => gerencia
                ? <button className="font-medium text-marca-700 underline" onClick={() => { setConvite(null); setEditando(p) }}>{p.nome}</button>
                : <span className="font-medium">{p.nome}</span>,
            },
            { titulo: 'CPF/CNPJ', valor: (p) => documento(p.documento) || '—' },
            { titulo: 'Contato', valor: (p) => [p.email, telefone(p.telefone)].filter(Boolean).join(' · ') || '—' },
            {
              titulo: 'Unidades',
              valor: (p) => (p.unidades ?? []).length === 0 ? '—'
                : (p.unidades ?? []).map((u) => `${u.unidade} (${vinculo(u.vinculo).toLowerCase()})`).join(', '),
            },
            {
              titulo: 'Acesso', direita: true,
              valor: (p) => p.temLogin ? <Etiqueta cor="verde">tem acesso</Etiqueta>
                : gerencia ? <button className="text-marca-700 underline" onClick={() => { setConvite(null); setConvidando(p) }}>Convidar</button>
                : <Etiqueta>sem acesso</Etiqueta>,
            },
          ]}
        />
      )}
    </div>
  )
}

function CadastroDeMorador({ aoTerminar }: { aoTerminar: (convite: Convite | null) => void }) {
  const cliente = useQueryClient()
  const unidades = useQuery({ queryKey: ['unidades', 'ativas'], queryFn: async () => dados(await api.GET('/api/unidades')) })
  const [nome, setNome] = useState('')
  const [doc, setDoc] = useState('')
  const [email, setEmail] = useState('')
  const [fone, setFone] = useState('')
  const [unidadeId, setUnidadeId] = useState('')
  const [tipo, setTipo] = useState('PROPRIETARIO')
  const [inicio, setInicio] = useState(hojeIso())
  const [convidar, setConvidar] = useState(true)

  const cadastrar = useMutation({
    mutationFn: async () => dados(await api.POST('/api/moradores', {
      body: {
        pessoa: { nome, documento: doc || undefined, email: email || undefined, telefone: fone || undefined },
        unidadeId, vinculo: tipo as 'PROPRIETARIO', inicio, convidar,
      },
    })),
    onSuccess: async (resposta) => {
      await cliente.invalidateQueries({ queryKey: ['pessoas'] })
      await cliente.invalidateQueries({ queryKey: ['unidades'] })
      aoTerminar(resposta.convite ?? null)
    },
  })

  function enviar(e: FormEvent) { e.preventDefault(); cadastrar.mutate() }

  return (
    <Cartao titulo="Cadastrar morador">
      <form onSubmit={enviar} className="grid gap-3 md:grid-cols-2">
        <Campo rotulo="Nome" required value={nome} onChange={(e) => setNome(e.target.value)} />
        <Campo rotulo="CPF ou CNPJ (opcional)" value={doc} onChange={(e) => setDoc(e.target.value)} />
        <Campo rotulo="E-mail" type="email" value={email} onChange={(e) => setEmail(e.target.value)} ajuda="Necessário para o convite de acesso." />
        <Campo rotulo="Celular (opcional)" type="tel" value={fone} onChange={(e) => setFone(e.target.value)} ajuda="Com DDD. Usado no WhatsApp." />
        <Selecao rotulo="Unidade" required value={unidadeId} onChange={(e) => setUnidadeId(e.target.value)} vazio="Escolha…"
          opcoes={(unidades.data ?? []).map((u) => ({ valor: u.id ?? '', rotulo: u.descricao ?? '' }))} />
        <Selecao rotulo="Vínculo" value={tipo} onChange={(e) => setTipo(e.target.value)} opcoes={opcoes(ROTULOS.vinculo)} />
        <Campo rotulo="Desde" type="date" required value={inicio} onChange={(e) => setInicio(e.target.value)} />
        <label className="flex items-center gap-2 self-end pb-3 text-sm">
          <input type="checkbox" checked={convidar} onChange={(e) => setConvidar(e.target.checked)} className="h-5 w-5 accent-marca-700" />
          Gerar convite de acesso ao app
        </label>
        <div className="md:col-span-2"><MensagemDeErro erro={cadastrar.error} /></div>
        <div className="flex gap-2 md:col-span-2">
          <Botao type="submit" carregando={cadastrar.isPending}>Cadastrar</Botao>
          <Botao type="button" variante="secundario" onClick={() => aoTerminar(null)}>Cancelar</Botao>
        </div>
      </form>
    </Cartao>
  )
}

function Convidar({ pessoa, aoTerminar }: { pessoa: Pessoa; aoTerminar: (convite: Convite | null) => void }) {
  const [email, setEmail] = useState(pessoa.email ?? '')
  const [perfil, setPerfil] = useState('MORADOR')
  const convidar = useMutation({
    mutationFn: async () => dados(await api.POST('/api/convites', {
      body: { pessoaId: pessoa.id ?? '', email, perfil: perfil as 'MORADOR' },
    })),
    onSuccess: (c) => aoTerminar(c),
  })
  return (
    <Cartao titulo={`Convidar ${pessoa.nome}`}>
      <form onSubmit={(e) => { e.preventDefault(); convidar.mutate() }} className="grid gap-3 md:grid-cols-2">
        <Campo rotulo="E-mail" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        <Selecao rotulo="Acesso como" value={perfil} onChange={(e) => setPerfil(e.target.value)}
          opcoes={[{ valor: 'MORADOR', rotulo: 'Morador' }, { valor: 'ADMINISTRADORA', rotulo: 'Administradora' }, { valor: 'PORTARIA', rotulo: 'Portaria' }]} />
        <div className="md:col-span-2"><MensagemDeErro erro={convidar.error} /></div>
        <div className="flex gap-2 md:col-span-2">
          <Botao type="submit" carregando={convidar.isPending}>Gerar convite</Botao>
          <Botao type="button" variante="secundario" onClick={() => aoTerminar(null)}>Cancelar</Botao>
        </div>
      </form>
    </Cartao>
  )
}

/** Corrigir nome, CPF/CNPJ (o banco exige no boleto), e-mail e celular. */
function EdicaoDePessoa({ pessoa, aoTerminar }: { pessoa: Pessoa; aoTerminar: () => void }) {
  const cache = useQueryClient()
  const [f, setF] = useState({ nome: pessoa.nome ?? '', documento: pessoa.documento ?? '', email: pessoa.email ?? '', telefone: pessoa.telefone ?? '' })
  const muda = (campo: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [campo]: e.target.value })
  const salvar = useMutation({
    mutationFn: async () => dados(await api.PUT('/api/pessoas/{id}', {
      params: { path: { id: pessoa.id ?? '' } },
      body: { nome: f.nome, documento: f.documento || undefined, email: f.email || undefined, telefone: f.telefone || undefined },
    })),
    onSuccess: () => {
      cache.invalidateQueries({ queryKey: ['pessoas'] })
      aoTerminar()
    },
  })
  return (
    <Cartao titulo={`Editar ${pessoa.nome}`}>
      <form onSubmit={(e: FormEvent) => { e.preventDefault(); salvar.mutate() }} className="grid gap-3 md:grid-cols-2">
        <Campo rotulo="Nome" required maxLength={150} value={f.nome} onChange={muda('nome')} />
        <Campo rotulo="CPF ou CNPJ" value={f.documento} onChange={muda('documento')} ajuda="Obrigatório para o boleto de quem paga a unidade." />
        <Campo rotulo="E-mail" type="email" value={f.email} onChange={muda('email')} />
        <Campo rotulo="Celular" type="tel" value={f.telefone} onChange={muda('telefone')} />
        <div className="md:col-span-2"><MensagemDeErro erro={salvar.error} /></div>
        <div className="flex gap-2 md:col-span-2">
          <Botao type="submit" carregando={salvar.isPending}>Salvar</Botao>
          <Botao type="button" variante="secundario" onClick={aoTerminar}>Cancelar</Botao>
        </div>
      </form>
    </Cartao>
  )
}
