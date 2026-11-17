/** Lê o conteúdo do token JWT (sem validar a assinatura: quem valida é a API). */
export interface ConteudoDoToken {
  sub: string
  email: string
  condominio?: string
  perfis: string[]
  exp: number
  suporte?: string
}

export function lerToken(token: string): ConteudoDoToken | null {
  try {
    const parte = token.split('.')[1]
    const base64 = parte.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(parte.length / 4) * 4, '=')
    const texto = new TextDecoder().decode(Uint8Array.from(atob(base64), (c) => c.charCodeAt(0)))
    return JSON.parse(texto) as ConteudoDoToken
  } catch {
    return null
  }
}

/** Considera vencido 30 segundos antes, para não mandar uma requisição que vai ser recusada. */
export function expirado(token: string, agora: number = Date.now()): boolean {
  const conteudo = lerToken(token)
  return !conteudo || conteudo.exp * 1000 - 30_000 <= agora
}
