import { describe, expect, it } from 'vitest'
import { chaveEmBytes } from './push'

describe('push', () => {
  it('converte a chave VAPID de Base64 URL nos bytes que o navegador pede', () => {
    // 0x04 0xff 0xfe: "BP_-" em Base64 URL (com - e _ no lugar de + e /).
    expect(Array.from(chaveEmBytes('BP_-'))).toEqual([0x04, 0xff, 0xfe])
  })
})
