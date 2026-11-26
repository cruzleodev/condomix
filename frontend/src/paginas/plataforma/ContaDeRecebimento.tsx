import { BotaoDeIcone } from '../../componentes/BotaoDeIcone'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { api, dados } from '../../api/cliente'
import type { AvisoDaContaDeRecebimento } from '../../api/tipos'
import { TextoParaCopiar } from '../../componentes/Boleto'
import { Selecao } from '../../componentes/formulario'
import { Aviso, Botao, Campo, Cartao, Carregando, Etiqueta, MensagemDeErro } from '../../componentes/ui'
import { dataHora, ROTULOS } from '../../util/formato'

type Gateway = 'ASAAS' | 'SIMULADO'
type Ambiente = 'SANDBOX' | 'PRODUCAO'

const CHAVE = ['plataforma', 'recebimento']

/**
 * A conta do Condomix no gateway, que recebe as assinaturas dos clientes. O administrador cola a chave de API
 * da conta Asaas do Condomix e cadastra lá o aviso (webhook) com o endereço e o token mostrados uma vez.
 */
export function ContaDeRecebimento() {
  const cache = useQueryClient()
  const [editando, setEditando] = useState(false)
  const [aviso, setAviso] = useState<AvisoDaContaDeRecebimento | null>(null)
  const situacao = useQuery({ queryKey: CHAVE, queryFn: async () => dados(await api.GET('/api/plataforma/recebimento')) })
  const desativar = useMutation({
    mutationFn: async () => dados(await api.POST('/api/plataforma/recebimento/desativacao')),
    onSuccess: () => cache.invalidateQueries({ queryKey: CHAVE }),
  })

  const titulo = 'Conta do Condomix no Asaas'
  if (situacao.isPending) return <Cartao titulo={titulo}><Carregando /></Cartao>
  if (situacao.isError) return <Cartao titulo={titulo}><MensagemDeErro erro={situacao.error} /></Cartao>
  const c = situacao.data.conta
  const disponiveis = (situacao.data.disponiveis ?? []) as Gateway[]

  if (aviso) {
    return (
      <Cartao titulo="Cobrança das assinaturas ligada">
        <div className="space-y-3 text-sm">
          <p className="text-slate-600">
            Falta um passo: na conta Asaas <strong>do Condomix</strong>, em <strong>Integrações → Webhooks</strong>, crie um
            webhook de <strong>cobranças</strong> com o endereço e o token abaixo. É ele que avisa quando um condomínio paga.
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
    return <Configuracao disponiveis={disponiveis}
      aoTerminar={(a) => { setEditando(false); setAviso(a); cache.invalidateQueries({ queryKey: CHAVE }) }}
      aoCancelar={() => setEditando(false)} />
  }
  return (
    <Cartao titulo={titulo}
      acao={<BotaoDeIcone icone={c?.configurada ? 'logins' : 'adicionar'} rotulo={c?.configurada ? 'Trocar a chave' : 'Ligar a conta'} onClick={() => setEditando(true)} />}>
      {c?.configurada ? (
        <div className="space-y-2 text-sm">
          <p>
            {c.ativa ? <Etiqueta cor="verde">Cobrando</Etiqueta> : <Etiqueta cor="cinza">Desligada</Etiqueta>}
            <span className="ml-2 text-slate-600">
              {ROTULOS.gateway[c.gateway ?? 'ASAAS']} · {ROTULOS.ambienteDoGateway[c.ambiente ?? 'SANDBOX']} · desde {dataHora(c.configuradaEm)}
            </span>
          </p>
          {c.ativa && (
            <Botao variante="secundario" carregando={desativar.isPending} onClick={() => {
              if (window.confirm('Desligar a cobrança das assinaturas? Não sai mais fatura nova; as já emitidas continuam valendo.')) desativar.mutate()
            }}>Desligar</Botao>
          )}
          <MensagemDeErro erro={desativar.error} />
        </div>
      ) : (
        <p className="text-sm text-slate-600">
          Ainda não ligada. Enquanto isso, nenhuma fatura é gerada e ninguém é suspenso por atraso.
        </p>
      )}
    </Cartao>
  )
}

function Configuracao({ disponiveis, aoTerminar, aoCancelar }: {
  disponiveis: Gateway[]; aoTerminar: (aviso: AvisoDaContaDeRecebimento) => void; aoCancelar: () => void
}) {
  const [gateway, setGateway] = useState<Gateway>(disponiveis.includes('ASAAS') ? 'ASAAS' : disponiveis[0])
  const [ambiente, setAmbiente] = useState<Ambiente>('SANDBOX')
  const [chave, setChave] = useState('')
  const salvar = useMutation({
    mutationFn: async () => dados(await api.PUT('/api/plataforma/recebimento', { body: { gateway, ambiente, chave } })),
    onSuccess: aoTerminar,
  })
  return (
    <Cartao titulo="Ligar a cobrança das assinaturas">
      <form onSubmit={(e: FormEvent) => { e.preventDefault(); salvar.mutate() }} className="space-y-3">
        <p className="text-sm text-slate-600">
          Use a chave de API da conta Asaas <strong>do Condomix</strong> (em Integrações → Chaves de API), a que recebe as
          mensalidades. O Condomix confere a chave e guarda cifrada.
        </p>
        <div className="grid gap-3 md:grid-cols-2">
          <Selecao rotulo="Gateway" value={gateway} onChange={(e) => setGateway(e.target.value as Gateway)}
            opcoes={disponiveis.map((g) => ({ valor: g, rotulo: ROTULOS.gateway[g] }))} />
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
