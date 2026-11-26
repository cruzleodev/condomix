import { Link } from 'react-router'
import { useSessao } from '../sessao/contexto'

/**
 * Política de privacidade (exigida pelas lojas e pela LGPD). Aberta, sem login.
 * Texto-base: deve ser revisado pelo responsável jurídico antes da publicação nas lojas.
 */
export function Privacidade() {
  const { logado } = useSessao()
  return (
    <div className="min-h-dvh bg-slate-50 px-4 pb-10 pt-[max(1.5rem,var(--area-segura-topo))]">
      <article className="mx-auto max-w-2xl space-y-4 rounded-2xl bg-white p-6 text-sm leading-relaxed text-slate-700 shadow-sm ring-1 ring-slate-200">
        <Link to={logado ? '/conta' : '/entrar'} className="text-marca-700 underline">← Voltar</Link>
        <h1 className="text-xl font-bold text-slate-900">Política de privacidade do Condomix</h1>
        <p className="text-xs text-slate-500">Versão de 22/11/2026.</p>

        <Secao titulo="Quem somos e quem cuida dos seus dados">
          O Condomix é um sistema de gestão de condomínios. Os dados do seu cadastro no condomínio (nome, documento,
          unidade, cobranças, reservas) pertencem ao <strong>condomínio</strong>, que decide como usá-los (é o controlador,
          pela LGPD). O Condomix guarda e processa esses dados em nome do condomínio (é o operador). Os dados do seu login
          (e-mail e senha) e das suas notificações são tratados pelo Condomix.
        </Secao>

        <Secao titulo="Quais dados usamos">
          <ul className="list-disc space-y-1 pl-5">
            <li>Login: e-mail e senha (guardada só como impressão cifrada, nunca em texto).</li>
            <li>Cadastro no condomínio: nome, CPF ou CNPJ, e-mail, telefone e as unidades em que você é proprietário, inquilino ou morador.</li>
            <li>Financeiro: cobranças, pagamentos e boletos das suas unidades.</li>
            <li>Reservas dos espaços comuns.</li>
            <li>Notificações e, se você ligar o push, o endereço do aparelho para recebê-las.</li>
            <li>Registros técnicos de acesso, para segurança.</li>
          </ul>
        </Secao>

        <Secao titulo="Para que usamos">
          Para você acessar o app do seu condomínio, ver e pagar cobranças, reservar espaços, receber avisos e consultar
          documentos; para o condomínio fazer a gestão e a prestação de contas; e para a segurança do sistema. Não vendemos
          seus dados nem os usamos para publicidade.
        </Secao>

        <Secao titulo="Com quem compartilhamos">
          Só com quem é preciso para o serviço funcionar: o gateway de pagamentos (Asaas), para emitir boletos e Pix, com
          seu nome, documento e e-mail; o provedor de e-mail, para enviar as notificações; os serviços de push do seu
          aparelho (Apple, Google); e o provedor de hospedagem. A gestão do seu condomínio vê os dados do condomínio.
        </Secao>

        <Secao titulo="Por quanto tempo">
          Enquanto o condomínio usar o Condomix e pelo prazo que a lei exigir (por exemplo, registros financeiros). Se você
          excluir a sua conta, o login, as notificações e os aparelhos do push são apagados na hora; o cadastro no condomínio
          e o histórico financeiro ficam com o condomínio, que é o responsável por eles.
        </Secao>

        <Secao titulo="Seus direitos">
          Você pode pedir acesso, correção, portabilidade e exclusão dos seus dados. A conta pode ser excluída pelo próprio
          app, em <strong>Conta → Excluir minha conta</strong>. Para os dados do cadastro no condomínio, fale com a gestão do
          seu condomínio. Para qualquer dúvida, escreva para <strong>privacidade@condomix.com.br</strong>.
        </Secao>

        <Secao titulo="Segurança">
          A comunicação é cifrada (https), as senhas e as chaves de acesso aos gateways ficam cifradas, e cada condomínio só
          vê os próprios dados.
        </Secao>
      </article>
    </div>
  )
}

function Secao({ titulo, children }: { titulo: string; children: React.ReactNode }) {
  return (
    <section>
      <h2 className="mb-1 font-semibold text-slate-900">{titulo}</h2>
      <div>{children}</div>
    </section>
  )
}
