import { useMutation, useQuery } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { api, dados } from '../api/cliente'
import { Botao, Campo, Cartao, Carregando, MensagemDeErro } from '../componentes/ui'
import { data, perfil } from '../util/formato'

/**
 * Tela do link de convite (/convite?codigo=...). Quem não tem login define a senha; quem já tem
 * (em outro condomínio) confirma com a senha atual.
 */
export function AceitarConvite() {
  const [parametros] = useSearchParams()
  const codigo = parametros.get('codigo') ?? ''
  const navegar = useNavigate()
  const [senha, setSenha] = useState('')
  const [confirmacao, setConfirmacao] = useState('')
  const [erroLocal, setErroLocal] = useState<string | null>(null)

  const convite = useQuery({
    queryKey: ['convite', codigo],
    enabled: codigo !== '',
    retry: false,
    queryFn: async () => dados(await api.POST('/api/convites/consulta', { body: { codigo } })),
  })
  const aceitar = useMutation({
    mutationFn: async () => dados(await api.POST('/api/convites/aceite', { body: { codigo, senha } })),
    onSuccess: (resposta) => navegar('/entrar', { replace: true, state: { email: resposta.email } }),
  })

  function enviar(e: FormEvent) {
    e.preventDefault()
    setErroLocal(null)
    if (!convite.data?.jaTemLogin && senha !== confirmacao) {
      setErroLocal('As senhas não conferem.')
      return
    }
    aceitar.mutate()
  }

  const c = convite.data
  return (
    <div className="flex min-h-dvh flex-col justify-center bg-marca-700 px-4 pb-[max(2.5rem,var(--area-segura-base))] pt-[max(2.5rem,var(--area-segura-topo))]">
      <div className="mx-auto w-full max-w-sm">
        <h1 className="mb-6 text-center text-2xl font-bold text-white">Convite para o Condomix</h1>
        <Cartao>
          {!codigo ? <MensagemDeErro erro="Link de convite incompleto. Peça um novo ao síndico." />
            : convite.isPending ? <Carregando />
            : convite.isError ? <MensagemDeErro erro={convite.error} />
            : c && (
              <form onSubmit={enviar} className="space-y-4">
                <div>
                  <p className="text-lg font-semibold">{c.condominio}</p>
                  <p className="text-sm text-slate-600">
                    Para <strong>{c.pessoa}</strong>{c.perfil ? ` como ${perfil(c.perfil).toLowerCase()}` : ''}.
                    Válido até {data(c.expiraEm)}.
                  </p>
                </div>
                <Campo rotulo="E-mail" value={c.email} disabled readOnly />
                {c.jaTemLogin ? (
                  <Campo rotulo="Sua senha atual" type="password" required autoComplete="current-password"
                    ajuda="Você já tem login no Condomix: este condomínio vai aparecer junto dos outros."
                    value={senha} onChange={(e) => setSenha(e.target.value)} />
                ) : (
                  <>
                    <Campo rotulo="Crie uma senha" type="password" required minLength={8} autoComplete="new-password"
                      ajuda="Pelo menos 8 caracteres." value={senha} onChange={(e) => setSenha(e.target.value)} />
                    <Campo rotulo="Repita a senha" type="password" required autoComplete="new-password"
                      value={confirmacao} onChange={(e) => setConfirmacao(e.target.value)} />
                  </>
                )}
                <MensagemDeErro erro={erroLocal ?? aceitar.error} />
                <Botao type="submit" carregando={aceitar.isPending} className="w-full">Aceitar convite</Botao>
              </form>
            )}
        </Cartao>
      </div>
    </div>
  )
}
