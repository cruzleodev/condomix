import { describe, expect, it } from 'vitest'
import { tokenDeTeste } from '../testes/tokens'
import { expirado, lerToken } from './jwt'

describe('jwt', () => {
  it('lê o conteúdo do token, inclusive com acentos', () => {
    const token = tokenDeTeste({ sub: '1', email: 'josé@teste.com', perfis: ['SINDICO'], exp: 2_000_000_000 })
    expect(lerToken(token)).toMatchObject({ email: 'josé@teste.com', perfis: ['SINDICO'] })
  })

  it('considera vencido um pouco antes da hora, e inválido como vencido', () => {
    const agora = 1_000_000_000_000
    expect(expirado(tokenDeTeste({ exp: agora / 1000 + 3600 }), agora)).toBe(false)
    expect(expirado(tokenDeTeste({ exp: agora / 1000 + 10 }), agora)).toBe(true)
    expect(expirado('isso-nao-e-um-token', agora)).toBe(true)
  })
})
