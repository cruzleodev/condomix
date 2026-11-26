import { CampoDinheiro } from '../../componentes/CampoNumerico'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api, dados } from '../../api/cliente'
import type { PlanoNoCatalogo } from '../../api/tipos'
import { Botao, Cartao, Carregando, Etiqueta, MensagemDeErro, TituloDaPagina } from '../../componentes/ui'
import { lerReais, moeda, ROTULOS } from '../../util/formato'

/** Catálogo de planos do Condomix: os módulos de cada plano e os preços por periodicidade. */
export function Planos() {
  const planos = useQuery({ queryKey: ['plataforma', 'planos'], queryFn: async () => dados(await api.GET('/api/plataforma/planos')) })
  return (
    <div className="space-y-4">
      <TituloDaPagina subtitulo="Preço de cada plano por periodicidade. Plano sem preço aparece com —.">Planos e preços</TituloDaPagina>
      {planos.isPending ? <Carregando /> : planos.isError ? <MensagemDeErro erro={planos.error} /> : (
        <ul className="grid gap-3 md:grid-cols-2">
          {(planos.data ?? []).map((p) => <li key={p.codigo}><CartaoDoPlano plano={p} /></li>)}
        </ul>
      )}
    </div>
  )
}

/** Valor para editar: 1234.5 → "1234,50"; sem preço (a API manda null) → vazio. */
const virgula = (n: number | null | undefined) => n == null ? '' : n.toFixed(2).replace('.', ',')

function CartaoDoPlano({ plano }: { plano: PlanoNoCatalogo }) {
  const cache = useQueryClient()
  const [editando, setEditando] = useState(false)
  const [precos, setPrecos] = useState({ mensal: '', semestral: '', anual: '' })
  const salvar = useMutation({
    mutationFn: async () => dados(await api.PUT('/api/plataforma/planos/{codigo}/precos', {
      params: { path: { codigo: plano.codigo! } },
      body: { mensal: lerReais(precos.mensal), semestral: lerReais(precos.semestral), anual: lerReais(precos.anual) },
    })),
    onSuccess: () => {
      cache.invalidateQueries({ queryKey: ['plataforma', 'planos'] })
      setEditando(false)
    },
  })
  function abrir() {
    setPrecos({ mensal: virgula(plano.precoMensal), semestral: virgula(plano.precoSemestral), anual: virgula(plano.precoAnual) })
    setEditando(true)
  }
  return (
    <Cartao>
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="font-semibold">{plano.nome}</p>
          {plano.descricao && <p className="text-sm text-slate-600">{plano.descricao}</p>}
        </div>
        <div className="flex gap-1">
          <Etiqueta cor={plano.tipo === 'BASE' ? 'marca' : 'cinza'}>{plano.tipo === 'BASE' ? 'Plano' : 'Adicional'}</Etiqueta>
          {!plano.ativo && <Etiqueta cor="vermelho">Inativo</Etiqueta>}
        </div>
      </div>
      <p className="mt-2 flex flex-wrap gap-1">
        {(plano.modulos ?? []).map((m) => <Etiqueta key={m}>{ROTULOS.modulo[m]}</Etiqueta>)}
      </p>
      {editando ? (
        <form onSubmit={(e) => { e.preventDefault(); salvar.mutate() }} className="mt-3 space-y-3">
          <div className="grid grid-cols-3 gap-2">
            <CampoDinheiro rotulo="Mensal" valor={precos.mensal} aoMudar={(v) => setPrecos({ ...precos, mensal: v })} />
            <CampoDinheiro rotulo="Semestral" valor={precos.semestral} aoMudar={(v) => setPrecos({ ...precos, semestral: v })} />
            <CampoDinheiro rotulo="Anual" valor={precos.anual} aoMudar={(v) => setPrecos({ ...precos, anual: v })} />
          </div>
          <MensagemDeErro erro={salvar.error} />
          <div className="flex gap-2">
            <Botao type="submit" carregando={salvar.isPending}>Salvar preços</Botao>
            <Botao type="button" variante="secundario" onClick={() => setEditando(false)}>Cancelar</Botao>
          </div>
        </form>
      ) : (
        <>
          <dl className="mt-3 grid grid-cols-3 gap-2 text-sm">
            <div><dt className="text-slate-500">Mensal</dt><dd className="font-medium">{moeda(plano.precoMensal)}</dd></div>
            <div><dt className="text-slate-500">Semestral</dt><dd className="font-medium">{moeda(plano.precoSemestral)}</dd></div>
            <div><dt className="text-slate-500">Anual</dt><dd className="font-medium">{moeda(plano.precoAnual)}</dd></div>
          </dl>
          <button className="mt-3 text-sm text-marca-700 underline" onClick={abrir}>Alterar preços</button>
        </>
      )}
    </Cartao>
  )
}
