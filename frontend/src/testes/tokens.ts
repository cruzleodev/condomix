/** Monta um token só com o conteúdo (a assinatura não importa no frontend). */
export function tokenDeTeste(conteudo: Record<string, unknown>): string {
  const base64url = (texto: string) => btoa(String.fromCharCode(...new TextEncoder().encode(texto)))
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  return `${base64url('{"alg":"HS256"}')}.${base64url(JSON.stringify(conteudo))}.assinatura`
}
