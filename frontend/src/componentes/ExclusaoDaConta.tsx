import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { api, dados } from '../api/cliente'
import { useSessao } from '../sessao/contexto'
import { Botao, Campo, Cartao, MensagemDeErro } from './ui'

/** Na Conta: excluir o próprio login (exigência da App Store). Pede a senha e explica o que fica com o condomínio. */
export function ExclusaoDaConta() {
  const { sair } = useSessao()
  const [aberto, setAberto] = useState(false)
  const [senha, setSenha] = useState('')
  const excluir = useMutation({
    mutationFn: async () => dados(await api.POST('/api/auth/exclusao-da-conta', { body: { senha } })),
    onSuccess: () => sair(),
  })
  if (!aberto) {
    return <Botao variante="perigo" className="w-full" onClick={() => setAberto(true)}>Excluir minha conta</Botao>
  }
  return (
    <Cartao titulo="Excluir minha conta">
      <form className="space-y-3" onSubmit={(e: FormEvent) => { e.preventDefault(); excluir.mutate() }}>
        <div className="space-y-2 text-sm text-slate-700">
          <p>Seu login será <strong>apagado agora</strong>, em todos os condomínios: você não entra mais no app, e as suas notificações e os aparelhos do push são apagados.</p>
          <p>Seu cadastro no condomínio (nome, unidade) e o histórico de cobranças e pagamentos <strong>ficam com o condomínio</strong>, que é o responsável por eles. Para isso, fale com a gestão do condomínio.</p>
          <p>Não dá para desfazer. Para voltar a usar, peça um novo convite à gestão.</p>
        </div>
        <Campo rotulo="Sua senha, para confirmar" type="password" autoComplete="current-password" required value={senha}
          onChange={(e) => setSenha(e.target.value)} />
        <MensagemDeErro erro={excluir.error} />
        <div className="flex flex-col gap-2 md:flex-row">
          <Botao type="submit" variante="perigo" carregando={excluir.isPending}>Excluir minha conta para sempre</Botao>
          <Botao type="button" variante="secundario" onClick={() => setAberto(false)}>Voltar</Botao>
        </div>
      </form>
    </Cartao>
  )
}
