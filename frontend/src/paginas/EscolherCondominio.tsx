import { useState } from 'react'
import { useNavigate } from 'react-router'
import { MensagemDeErro, TituloDaPagina } from '../componentes/ui'
import { useSessao } from '../sessao/contexto'
import { perfil } from '../util/formato'

/** Para quem tem acesso a mais de um condomínio (síndico profissional, administradora...). */
export function EscolherCondominio() {
  const { condominios, condominioId, escolherCondominio, sair } = useSessao()
  const navegar = useNavigate()
  const [erro, setErro] = useState<unknown>(null)
  const [escolhendo, setEscolhendo] = useState<string | null>(null)

  async function escolher(id: string) {
    setErro(null)
    setEscolhendo(id)
    try {
      await escolherCondominio(id)
      navegar('/', { replace: true })
    } catch (e) {
      setErro(e)
    } finally {
      setEscolhendo(null)
    }
  }

  return (
    <div className="mx-auto max-w-2xl px-4 py-6">
      <TituloDaPagina subtitulo="Você tem acesso a mais de um. Pode trocar depois, em Conta.">Escolha o condomínio</TituloDaPagina>
      <MensagemDeErro erro={erro} />
      <ul className="mt-3 space-y-3">
        {condominios.map((c) => (
          <li key={c.id}>
            <button
              onClick={() => escolher(c.id ?? '')}
              disabled={escolhendo !== null}
              className="w-full rounded-2xl bg-white p-4 text-left shadow-sm ring-1 ring-slate-200 hover:ring-marca-600 disabled:opacity-60"
            >
              <span className="block font-semibold text-slate-900">{c.nome}</span>
              <span className="text-sm text-slate-500">
                {(c.perfis ?? []).map(perfil).join(', ')}
                {c.id === condominioId && ' · atual'}
                {escolhendo === c.id && ' · entrando…'}
              </span>
              {c.pagamentoPendente && (
                <span className="mt-1 block text-sm font-medium text-red-700">Acesso suspenso: entre para pagar a fatura do Condomix</span>
              )}
            </button>
          </li>
        ))}
      </ul>
      <button onClick={sair} className="mt-6 text-sm text-slate-500 underline">Sair</button>
    </div>
  )
}
