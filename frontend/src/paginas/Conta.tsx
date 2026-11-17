import { useNavigate } from 'react-router'
import { Botao, Cartao, Etiqueta, TituloDaPagina } from '../componentes/ui'
import { useSessao } from '../sessao/contexto'
import { perfil } from '../util/formato'

export function Conta() {
  const { email, condominio, condominios, perfis, sair } = useSessao()
  const navegar = useNavigate()
  return (
    <div className="space-y-4 md:max-w-xl">
      <TituloDaPagina>Conta</TituloDaPagina>
      <Cartao titulo="Você">
        <p className="break-all">{email}</p>
        <div className="mt-2 flex flex-wrap gap-1">{perfis.map((p) => <Etiqueta key={p} cor="marca">{perfil(p)}</Etiqueta>)}</div>
      </Cartao>
      <Cartao titulo="Condomínio">
        <p>{condominio?.nome}</p>
        {condominios.length > 1 && (
          <Botao variante="secundario" className="mt-3 w-full" onClick={() => navegar('/condominio')}>Trocar de condomínio</Botao>
        )}
      </Cartao>
      <Botao variante="perigo" className="w-full" onClick={sair}>Sair</Botao>
    </div>
  )
}
