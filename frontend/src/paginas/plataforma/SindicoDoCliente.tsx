import { BotaoDeIcone } from '../../componentes/BotaoDeIcone'
import { CampoData } from '../../componentes/CampoData'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent, type ReactNode } from 'react'
import { api, dados } from '../../api/cliente'
import type { ConviteGerado, SindicoAtual, SindicoRegistrado } from '../../api/tipos'
import { LinkDeConvite } from '../../componentes/formulario'
import { Botao, Campo, Cartao, Carregando, Etiqueta, MensagemDeErro } from '../../componentes/ui'
import { data, dataHora, hojeIso, umAnoDepoisMenosUmDia } from '../../util/formato'

const chaveDoSindico = (id: string) => ['plataforma', 'sindico', id]

/** O síndico do cliente: quem é, se já criou o login, novo convite e troca de síndico. */
export function SecaoDoSindico({ condominioId }: { condominioId: string }) {
  const [modo, setModo] = useState<'ver' | 'convite' | 'troca'>('ver')
  const sindico = useQuery({
    queryKey: chaveDoSindico(condominioId),
    // 204 (sem síndico registrado) vem sem corpo.
    queryFn: async () => dados(await api.GET('/api/plataforma/condominios/{id}/sindico', { params: { path: { id: condominioId } } })) ?? null,
  })
  if (sindico.isPending) return <Cartao titulo="Síndico"><Carregando /></Cartao>
  if (sindico.isError) return <Cartao titulo="Síndico"><MensagemDeErro erro={sindico.error} /></Cartao>
  const s = sindico.data

  if (modo === 'convite' && s) return <NovoConvite condominioId={condominioId} sindico={s} aoTerminar={() => setModo('ver')} />
  if (modo === 'troca') return <TrocaDeSindico condominioId={condominioId} aoTerminar={() => setModo('ver')} />
  return (
    <Cartao titulo="Síndico" acao={<BotaoDeIcone icone="editar" rotulo="Trocar o síndico" onClick={() => setModo('troca')} />}>
      {s ? (
        <>
          <dl className="grid gap-1 text-sm md:grid-cols-2">
            <Linha rotulo="Nome">{s.nome}</Linha>
            <Linha rotulo="E-mail">{s.email ?? '—'}</Linha>
            <Linha rotulo="Mandato">{data(s.inicioMandato)} a {data(s.fimMandato)}</Linha>
            <Linha rotulo="Login">
              {s.temLogin ? <Etiqueta cor="verde">Já criou</Etiqueta>
                : s.convitePendenteAte ? <Etiqueta cor="amarelo">Convite vale até {dataHora(s.convitePendenteAte)}</Etiqueta>
                  : <Etiqueta cor="vermelho">Convite vencido</Etiqueta>}
            </Linha>
          </dl>
          {!s.temLogin && (
            <Botao className="mt-3 w-full md:w-auto" onClick={() => setModo('convite')}>Gerar novo convite</Botao>
          )}
        </>
      ) : <p className="text-sm text-slate-600">Este condomínio ainda não tem síndico registrado.</p>}
    </Cartao>
  )
}

function NovoConvite({ condominioId, sindico, aoTerminar }: { condominioId: string; sindico: SindicoAtual; aoTerminar: () => void }) {
  const cache = useQueryClient()
  const [email, setEmail] = useState(sindico.email ?? '')
  const [gerado, setGerado] = useState<ConviteGerado | null>(null)
  const gerar = useMutation({
    mutationFn: async () => dados(await api.POST('/api/plataforma/condominios/{id}/sindico/convite', {
      params: { path: { id: condominioId } }, body: { email: email.trim() || undefined },
    })),
    onSuccess: (convite) => {
      setGerado(convite)
      cache.invalidateQueries({ queryKey: chaveDoSindico(condominioId) })
    },
  })
  return (
    <Cartao titulo={`Novo convite para ${sindico.nome}`}>
      {gerado ? (
        <>
          <p className="mb-3 text-sm text-slate-600">O link anterior deixou de valer. Envie este ao síndico:</p>
          {gerado.link && <LinkDeConvite link={gerado.link} email={gerado.email} enviadoPorEmail={gerado.enviadoPorEmail} />}
          <Botao variante="secundario" className="mt-3 w-full md:w-auto" onClick={aoTerminar}>Fechar</Botao>
        </>
      ) : (
        <form onSubmit={(e: FormEvent) => { e.preventDefault(); gerar.mutate() }} className="space-y-3">
          <Campo rotulo="E-mail do síndico" type="email" required value={email} onChange={(e) => setEmail(e.target.value)}
            ajuda="Corrija aqui se o e-mail do cadastro estava errado." />
          <MensagemDeErro erro={gerar.error} />
          <div className="flex flex-col gap-2 md:flex-row">
            <Botao type="submit" carregando={gerar.isPending}>Gerar link</Botao>
            <Botao type="button" variante="secundario" onClick={aoTerminar}>Cancelar</Botao>
          </div>
        </form>
      )}
    </Cartao>
  )
}

function TrocaDeSindico({ condominioId, aoTerminar }: { condominioId: string; aoTerminar: () => void }) {
  const cache = useQueryClient()
  const hoje = hojeIso()
  const [f, setF] = useState({ nome: '', email: '', documento: '', inicio: hoje, fim: umAnoDepoisMenosUmDia(hoje) })
  const [registrado, setRegistrado] = useState<SindicoRegistrado | null>(null)
  const muda = (campo: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [campo]: e.target.value })
  const trocar = useMutation({
    mutationFn: async () => dados(await api.PUT('/api/plataforma/condominios/{id}/sindico', {
      params: { path: { id: condominioId } },
      body: { nome: f.nome, email: f.email, documento: f.documento || undefined, inicioMandato: f.inicio, fimMandato: f.fim },
    })),
    onSuccess: (resultado) => {
      setRegistrado(resultado)
      cache.invalidateQueries({ queryKey: chaveDoSindico(condominioId) })
    },
  })

  if (registrado) {
    return (
      <Cartao titulo={`${registrado.sindico?.nome} registrado como síndico`}>
        <p className="mb-3 text-sm text-slate-600">O mandato anterior termina na véspera da posse.</p>
        {registrado.convite?.link
          ? <LinkDeConvite link={registrado.convite.link} email={registrado.convite.email} enviadoPorEmail={registrado.convite.enviadoPorEmail} />
          : <p className="text-sm text-slate-600">Essa pessoa já tem login no Condomix: entra com a senha dela, já como síndico.</p>}
        <Botao variante="secundario" className="mt-3 w-full md:w-auto" onClick={aoTerminar}>Fechar</Botao>
      </Cartao>
    )
  }
  return (
    <Cartao titulo="Trocar o síndico">
      <form onSubmit={(e: FormEvent) => { e.preventDefault(); trocar.mutate() }} className="space-y-3">
        <p className="text-sm text-slate-600">
          Use quando o condomínio elegeu outro síndico e ele ainda não consegue entrar. O síndico atual também pode
          registrar a eleição pela tela Cargos e mandatos.
        </p>
        <div className="grid gap-3 md:grid-cols-2">
          <Campo rotulo="Nome" required maxLength={150} value={f.nome} onChange={muda('nome')} />
          <Campo rotulo="E-mail" type="email" required value={f.email} onChange={muda('email')} />
          <Campo rotulo="CPF ou CNPJ (opcional)" value={f.documento} onChange={muda('documento')}
            ajuda="Se já for morador do condomínio, informe para não duplicar o cadastro." />
          <div className="grid grid-cols-2 gap-3">
            <CampoData rotulo="Posse" required valor={f.inicio}
              aoMudar={(v) => setF({ ...f, inicio: v, fim: v ? umAnoDepoisMenosUmDia(v) : f.fim })} />
            <CampoData rotulo="Fim do mandato" required valor={f.fim} aoMudar={(v) => setF({ ...f, fim: v })} />
          </div>
        </div>
        <MensagemDeErro erro={trocar.error} />
        <div className="flex flex-col gap-2 md:flex-row">
          <Botao type="submit" carregando={trocar.isPending}>Registrar e gerar convite</Botao>
          <Botao type="button" variante="secundario" onClick={aoTerminar}>Cancelar</Botao>
        </div>
      </form>
    </Cartao>
  )
}

export function Linha({ rotulo, children }: { rotulo: string; children: ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-3 md:justify-start">
      <dt className="text-slate-500 md:w-24">{rotulo}</dt>
      <dd className="text-right md:text-left">{children}</dd>
    </div>
  )
}
