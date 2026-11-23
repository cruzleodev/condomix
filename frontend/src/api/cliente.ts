import createClient, { type Middleware } from 'openapi-fetch'
import type { paths } from './schema'
import { tokenAtual, avisarSessaoExpirada } from '../sessao/armazenamento'

/**
 * Cliente da API, tipado pelo OpenAPI. Coloca o token em todas as requisições e, se a API responder 401
 * (token vencido ou inválido), encerra a sessão.
 */
/**
 * Onde está a API. No site, é a mesma origem da página (em desenvolvimento o Vite repassa /api para o backend).
 * No app nativo publicado, a página vem de dentro do app, então a API é o endereço público (VITE_API_URL).
 */
export const enderecoDaApi: string = import.meta.env.VITE_API_URL ?? globalThis.location?.origin ?? ''

export const api = createClient<paths>({
  baseUrl: enderecoDaApi,
  // Resolve o fetch a cada chamada (e não uma vez só), para que possa ser substituído nos testes.
  fetch: (requisicao) => globalThis.fetch(requisicao),
})

const autenticacao: Middleware = {
  onRequest({ request }) {
    const token = tokenAtual()
    if (token) request.headers.set('Authorization', `Bearer ${token}`)
    return request
  },
  onResponse({ response }) {
    if (response.status === 401 && tokenAtual()) avisarSessaoExpirada()
    return response
  },
}
api.use(autenticacao)

/** Erro da API com a mensagem para o usuário (campo "detail" do formato RFC 9457). */
export class ErroDaApi extends Error {
  readonly status: number

  constructor(status: number, mensagem: string) {
    super(mensagem)
    this.status = status
  }
}

/** Devolve os dados ou lança {@link ErroDaApi} com a mensagem da API. */
export function dados<T>(resultado: { data?: T; error?: unknown; response: Response }): T {
  if (resultado.error !== undefined || !resultado.response.ok) {
    throw new ErroDaApi(resultado.response.status, mensagemDe(resultado.error, resultado.response.status))
  }
  return resultado.data as T
}

export function mensagemDe(erro: unknown, status?: number): string {
  if (erro && typeof erro === 'object' && 'detail' in erro && typeof erro.detail === 'string') return erro.detail
  if (erro instanceof Error) return erro.message
  if (status === 403) return 'Você não tem permissão para isto.'
  if (status && status >= 500) return 'O servidor teve um problema. Tente de novo em instantes.'
  return 'Não foi possível concluir. Verifique a conexão e tente de novo.'
}

/** Para o que o cliente tipado não cobre bem: envio de arquivos (multipart) e download de arquivos. */
export async function requisicaoAutenticada(url: string, opcoes: RequestInit = {}): Promise<Response> {
  const cabecalhos = new Headers(opcoes.headers)
  const token = tokenAtual()
  if (token) cabecalhos.set('Authorization', `Bearer ${token}`)
  const resposta = await fetch(url.startsWith('/') ? enderecoDaApi + url : url, { ...opcoes, headers: cabecalhos })
  if (resposta.status === 401 && token) avisarSessaoExpirada()
  if (!resposta.ok) {
    const corpo = await resposta.json().catch(() => undefined)
    throw new ErroDaApi(resposta.status, mensagemDe(corpo, resposta.status))
  }
  return resposta
}
