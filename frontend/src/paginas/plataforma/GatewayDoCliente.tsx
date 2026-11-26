import { BotaoDeIcone } from '../../componentes/BotaoDeIcone'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { api, dados } from '../../api/cliente'
import type { AvisoParaConfigurar } from '../../api/tipos'
import { TextoParaCopiar } from '../../componentes/Boleto'
import { Selecao } from '../../componentes/formulario'
import { Aviso, Botao, Campo, Cartao, Carregando, Etiqueta, MensagemDeErro } from '../../componentes/ui'
import { dataHora, ROTULOS } from '../../util/formato'

type Gateway = 'ASAAS' | 'SIMULADO'
type Ambiente = 'SANDBOX' | 'PRODUCAO'

/**
 * Boleto e Pix do cliente: o administrador cola a chave de API da conta Asaas do condomínio (o dinheiro cai
 * direto nela). Depois, cadastra no Asaas o aviso (webhook) com o endereço e o token mostrados uma vez.
 */
export function GatewayDoCliente({ condominioId }: { condominioId: string }) {
  const cache = useQueryClient()
  const chave = ['plataforma', 'gateway', condominioId]
  const [editando, setEditando] = useState(false)
  const [aviso, setAviso] = useState<AvisoParaConfigurar | null>(null)
  const situacao = useQuery({
    queryKey: chave,
    queryFn: async () => dados(await api.GET('/api/plataforma/condominios/{id}/gateway', { params: { path: { id: condominioId } } })),
  })
  const desativar = useMutation({
    mutationFn: async () => dados(await api.POST('/api/plataforma/condominios/{id}/gateway/desativacao', { params: { path: { id: condominioId } } })),
    onSuccess: () => cache.invalidateQueries({ queryKey: chave }),
  })

  if (situacao.isPending) return <Cartao titulo="Boleto e Pix"><Carregando /></Cartao>
  if (situacao.isError) return <Cartao titulo="Boleto e Pix"><MensagemDeErro erro={situacao.error} /></Cartao>
  const s = situacao.data.situacao
  const disponiveis = (situacao.data.disponiveis ?? []) as Gateway[]

  if (aviso) {
    return (
      <Cartao titulo="Boleto e Pix ligados">
        <div className="space-y-3 text-sm">
          <p className="text-slate-600">
            Falta um passo: na conta Asaas do condomínio, em <strong>Integrações → Webhooks</strong>, crie um webhook de
            <strong> cobranças</strong> com o endereço e o token abaixo. É ele que avisa o Condomix quando o morador paga.
          </p>
          <TextoParaCopiar rotulo="Endereço do webhook" texto={aviso.url ?? ''} />
          <TextoParaCopiar rotulo="Token de autenticação" texto={aviso.token ?? ''} />
          <Aviso>O token só aparece agora. Se perder, configure a chave de novo para gerar outro.</Aviso>
          <Botao variante="secundario" className="w-full md:w-auto" onClick={() => setAviso(null)}>Pronto</Botao>
        </div>
      </Cartao>
    )
  }
  if (editando) {
    return <Configuracao condominioId={condominioId} disponiveis={disponiveis}
      aoTerminar={(a) => { setEditando(false); setAviso(a); cache.invalidateQueries({ queryKey: chave }) }}
      aoCancelar={() => setEditando(false)} />
  }
  return (
    <Cartao titulo="Boleto e Pix"
      acao={<BotaoDeIcone icone={s?.configurado ? 'logins' : 'adicionar'} rotulo={s?.configurado ? 'Trocar a chave' : 'Ligar a conta'} onClick={() => setEditando(true)} />}>
      {s?.configurado ? (
        <div className="space-y-2 text-sm">
          <p>
            {s.ativo ? <Etiqueta cor="verde">Ligado</Etiqueta> : <Etiqueta cor="cinza">Desligado</Etiqueta>}
            <span className="ml-2 text-slate-600">
              {ROTULOS.gateway[s.gateway ?? 'ASAAS']} · {ROTULOS.ambienteDoGateway[s.ambiente ?? 'SANDBOX']} · desde {dataHora(s.configuradoEm)}
            </span>
          </p>
          {s.ativo && (
            <Botao variante="secundario" carregando={desativar.isPending} onClick={() => {
              if (window.confirm('Desligar o boleto e o Pix? As cobranças novas sairão sem boleto; os já emitidos continuam valendo.')) desativar.mutate()
            }}>Desligar</Botao>
          )}
          <MensagemDeErro erro={desativar.error} />
        </div>
      ) : (
        <p className="text-sm text-slate-600">
          Ainda não ligado. As cobranças deste condomínio saem sem boleto, e o síndico dá baixa à mão.
        </p>
      )}
    </Cartao>
  )
}

function Configuracao({ condominioId, disponiveis, aoTerminar, aoCancelar }: {
  condominioId: string; disponiveis: Gateway[]; aoTerminar: (aviso: AvisoParaConfigurar) => void; aoCancelar: () => void
}) {
  const [gateway, setGateway] = useState<Gateway>(disponiveis.includes('ASAAS') ? 'ASAAS' : disponiveis[0])
  const [ambiente, setAmbiente] = useState<Ambiente>('SANDBOX')
  const [chave, setChave] = useState('')
  const salvar = useMutation({
    mutationFn: async () => dados(await api.PUT('/api/plataforma/condominios/{id}/gateway', {
      params: { path: { id: condominioId } }, body: { gateway, ambiente, chave },
    })),
    onSuccess: aoTerminar,
  })
  const rotulosDisponiveis = Object.fromEntries(disponiveis.map((g) => [g, ROTULOS.gateway[g]]))
  return (
    <Cartao titulo="Ligar o boleto e o Pix">
      <form onSubmit={(e: FormEvent) => { e.preventDefault(); salvar.mutate() }} className="space-y-3">
        <p className="text-sm text-slate-600">
          Use a chave de API da conta Asaas <strong>do condomínio</strong> (em Integrações → Chaves de API). O Condomix confere a
          chave e guarda cifrada.
        </p>
        <div className="grid gap-3 md:grid-cols-2">
          <Selecao rotulo="Gateway" value={gateway} onChange={(e) => setGateway(e.target.value as Gateway)}
            opcoes={Object.entries(rotulosDisponiveis).map(([valor, rotulo]) => ({ valor, rotulo }))} />
          <Selecao rotulo="Ambiente" value={ambiente} onChange={(e) => setAmbiente(e.target.value as Ambiente)}
            opcoes={Object.entries(ROTULOS.ambienteDoGateway).map(([valor, rotulo]) => ({ valor, rotulo }))} />
        </div>
        <Campo rotulo="Chave de API" type="password" autoComplete="off" required value={chave} onChange={(e) => setChave(e.target.value)} />
        <MensagemDeErro erro={salvar.error} />
        <div className="flex flex-col gap-2 md:flex-row">
          <Botao type="submit" carregando={salvar.isPending}>Conferir e ligar</Botao>
          <Botao type="button" variante="secundario" onClick={aoCancelar}>Cancelar</Botao>
        </div>
      </form>
    </Cartao>
  )
}
