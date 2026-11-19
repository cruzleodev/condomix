import { useState, type FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { Botao, Campo, MensagemDeErro } from '../componentes/ui'
import { useSessao } from '../sessao/contexto'

export function Entrar() {
  const { entrar } = useSessao()
  const navegar = useNavigate()
  const local = useLocation()
  const [email, setEmail] = useState<string>((local.state as { email?: string } | null)?.email ?? '')
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState<unknown>(null)
  const [enviando, setEnviando] = useState(false)

  async function enviar(evento: FormEvent) {
    evento.preventDefault()
    setErro(null)
    setEnviando(true)
    try {
      const resultado = await entrar(email, senha)
      const destino = (local.state as { de?: string } | null)?.de ?? '/'
      navegar(resultado === 'pronto' ? destino : resultado === 'escolher' ? '/condominio' : '/', { replace: true })
    } catch (e) {
      setErro(e)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="flex min-h-dvh flex-col justify-center bg-marca-700 px-4 py-10">
      <div className="mx-auto w-full max-w-sm">
        <div className="mb-8 text-center text-white">
          <img src="/icone.svg" alt="" className="mx-auto mb-3 h-16 w-16 rounded-2xl" />
          <h1 className="text-2xl font-bold">Condomix</h1>
          <p className="text-marca-100">Seu condomínio na palma da mão</p>
        </div>
        <form onSubmit={enviar} className="space-y-4 rounded-2xl bg-white p-6 shadow-lg">
          <Campo rotulo="E-mail" type="email" autoComplete="username" required value={email}
            onChange={(e) => setEmail(e.target.value)} />
          <Campo rotulo="Senha" type="password" autoComplete="current-password" required value={senha}
            onChange={(e) => setSenha(e.target.value)} />
          <MensagemDeErro erro={erro} />
          <Botao type="submit" carregando={enviando} className="w-full">Entrar</Botao>
        </form>
      </div>
    </div>
  )
}
