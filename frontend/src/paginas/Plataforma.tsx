import { Cartao, TituloDaPagina } from '../componentes/ui'
import { useSessao } from '../sessao/contexto'

/** O painel do administrador da plataforma chega na etapa 11. Por ora, só orienta. */
export function Plataforma() {
  const { tem, email, sair, condominios } = useSessao()
  return (
    <div className="mx-auto max-w-2xl px-4 py-6">
      <TituloDaPagina subtitulo={email}>{tem('ADMIN_PLATAFORMA') ? 'Administração do Condomix' : 'Sem acesso'}</TituloDaPagina>
      <Cartao>
        {tem('ADMIN_PLATAFORMA') ? (
          <p className="text-sm text-slate-600">
            O painel da plataforma (clientes, planos, assinaturas e suporte) chega na próxima etapa.
            Enquanto isso, use o Swagger em <code>/swagger-ui/index.html</code>.
          </p>
        ) : (
          <p className="text-sm text-slate-600">
            Seu login ainda não tem acesso a nenhum condomínio{condominios.length === 0 ? '' : ' ativo'}.
            Peça ao síndico um convite.
          </p>
        )}
      </Cartao>
      <button onClick={sair} className="mt-6 text-sm text-slate-500 underline">Sair</button>
    </div>
  )
}
