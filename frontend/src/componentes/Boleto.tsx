import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api, dados } from '../api/cliente'
import type { BoletoDaCobranca, FormasDePagamento, PagamentoDaFatura } from '../api/tipos'
import { abrirForaDoApp } from '../nativo/app'
import { ROTULOS, data, moeda } from '../util/formato'
import { Aviso, Botao, Carregando, Etiqueta, MensagemDeErro } from './ui'

const COR_DO_BOLETO = {
  PENDENTE: 'amarelo', EMITIDO: 'marca', FALHOU: 'vermelho', PAGO: 'verde', A_CANCELAR: 'cinza', CANCELADO: 'cinza',
} as const

/** A situação do boleto de uma cobrança, numa etiqueta. */
export function EtiquetaDoBoleto({ boleto }: { boleto?: BoletoDaCobranca | null }) {
  if (!boleto?.situacao) return null
  return <Etiqueta cor={COR_DO_BOLETO[boleto.situacao]}>{ROTULOS.situacaoDoBoleto[boleto.situacao]}</Etiqueta>
}

/** Um texto longo (Pix copia e cola, linha digitável) com o botão de copiar. */
export function TextoParaCopiar({ rotulo, texto }: { rotulo: string; texto: string }) {
  const [copiado, setCopiado] = useState(false)
  async function copiar() {
    try {
      await navigator.clipboard.writeText(texto)
      setCopiado(true)
      setTimeout(() => setCopiado(false), 2500)
    } catch {
      window.prompt(rotulo, texto)
    }
  }
  return (
    <div>
      <p className="mb-1 text-sm font-medium text-slate-700">{rotulo}</p>
      <p className="mb-2 break-all rounded-lg bg-slate-50 p-2 font-mono text-xs text-slate-700 ring-1 ring-slate-200">{texto}</p>
      <Botao type="button" variante="secundario" onClick={copiar} className="w-full md:w-auto">
        {copiado ? 'Copiado ✓' : 'Copiar'}
      </Botao>
    </div>
  )
}

/** O que é preciso para pagar: Pix (QR Code e copia e cola), linha digitável e o boleto em PDF. */
export function DadosParaPagar({ v }: { v: FormasDePagamento | PagamentoDaFatura }) {
  return (
    <div className="space-y-4">
      <p className="text-sm text-slate-600">
        Boleto de <strong>{moeda(v.valor)}</strong>, vence em <strong>{data(v.vencimento)}</strong>.
        Pagando depois, o banco já cobra a multa e os juros.
      </p>
      {v.pixCopiaECola && (
        <section className="space-y-2">
          <h3 className="font-semibold">Pix</h3>
          {v.pixQrCode && (
            <img src={`data:image/png;base64,${v.pixQrCode}`} alt="QR Code do Pix" className="h-48 w-48 rounded-lg ring-1 ring-slate-200" />
          )}
          <TextoParaCopiar rotulo="Pix copia e cola" texto={v.pixCopiaECola} />
        </section>
      )}
      {v.linhaDigitavel && (
        <section className="space-y-2">
          <h3 className="font-semibold">Boleto</h3>
          <TextoParaCopiar rotulo="Linha digitável" texto={v.linhaDigitavel} />
          {v.url && (
            <a href={v.url} target="_blank" rel="noreferrer" className="inline-block text-sm text-marca-700 underline"
              onClick={(e) => { e.preventDefault(); abrirForaDoApp(v.url ?? '') }}>
              Abrir o boleto em PDF
            </a>
          )}
        </section>
      )}
      {v.gateway === 'SIMULADO' && <p className="text-xs text-slate-500">Boleto de teste (gateway simulado): não pague.</p>}
    </div>
  )
}

/**
 * A 2ª via de uma cobrança do condomínio.
 * {@code dono}: "eu" para o morador (só as próprias cobranças); "gestao" para o síndico mandar a alguém.
 */
export function SegundaVia({ cobrancaId, dono }: { cobrancaId: string; dono: 'eu' | 'gestao' }) {
  const via = useQuery({
    queryKey: ['cobrancas', 'segunda-via', cobrancaId],
    queryFn: async () => dados(dono === 'eu'
      ? await api.GET('/api/eu/cobrancas/{id}/pagamento', { params: { path: { id: cobrancaId } } })
      : await api.GET('/api/cobrancas/{id}/pagamento', { params: { path: { id: cobrancaId } } })),
  })
  if (via.isPending) return <Carregando texto="Buscando o boleto…" />
  if (via.isError) return <MensagemDeErro erro={via.error} />
  return <DadosParaPagar v={via.data} />
}

/**
 * Para a gestão, no alto da tela de cobranças: se o boleto e o Pix estão ligados e o botão para emitir
 * os boletos que faltam (ex.: cobranças de antes de ligar o gateway).
 */
export function BarraDoGateway({ gerencia }: { gerencia: boolean }) {
  const cache = useQueryClient()
  const gateway = useQuery({ queryKey: ['financeiro', 'gateway'], queryFn: async () => dados(await api.GET('/api/financeiro/gateway')) })
  const emitir = useMutation({
    mutationFn: async () => dados(await api.POST('/api/cobrancas/boletos')),
    onSuccess: () => cache.invalidateQueries({ queryKey: ['cobrancas'] }),
  })
  if (!gateway.data) return null
  if (!gateway.data.ligado) {
    return <Aviso>O boleto e o Pix ainda não estão ligados para este condomínio. Fale com o suporte do Condomix.</Aviso>
  }
  return (
    <div className="flex flex-col gap-2 rounded-xl bg-white p-3 text-sm ring-1 ring-slate-200 md:flex-row md:items-center md:justify-between">
      <p>
        <Etiqueta cor="verde">Boleto e Pix ligados</Etiqueta>
        <span className="ml-2 text-slate-600">
          pelo {ROTULOS.gateway[gateway.data.gateway ?? 'ASAAS']}. As cobranças novas já saem com boleto, e o pagamento dá baixa sozinho.
        </span>
      </p>
      {gerencia && (
        <span className="flex flex-col items-start gap-1 md:items-end">
          <Botao variante="secundario" carregando={emitir.isPending} onClick={() => emitir.mutate()}>Emitir os boletos que faltam</Botao>
          {emitir.data && <span className="text-xs text-slate-500">{emitir.data.pedidos} boleto(s) pedido(s). Ficam prontos em alguns segundos.</span>}
          <MensagemDeErro erro={emitir.error} />
        </span>
      )}
    </div>
  )
}
