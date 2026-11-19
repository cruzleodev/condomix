import { Cartao, TituloDaPagina } from '../componentes/ui'
import { useSessao } from '../sessao/contexto'

/** Login válido, mas sem nenhum condomínio ativo (ex.: convite ainda não aceito, condomínio suspenso). */
export function SemAcesso() {
  const { email, sair } = useSessao()
  return (
    <div className="mx-auto max-w-2xl px-4 py-6">
      <TituloDaPagina subtitulo={email}>Sem acesso a condomínios</TituloDaPagina>
      <Cartao>
        <p className="text-sm text-slate-600">
          Seu login ainda não tem acesso a nenhum condomínio ativo. Peça um convite ao síndico ou à administradora.
        </p>
      </Cartao>
      <button onClick={sair} className="mt-6 text-sm text-slate-500 underline">Sair</button>
    </div>
  )
}
