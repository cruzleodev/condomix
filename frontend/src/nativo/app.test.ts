import { describe, expect, it } from 'vitest'
import { caminhoDoLink, noAppNativo, nomeSeguro } from './app'

describe('app nativo', () => {
  it('nos testes (navegador), não é o app nativo', () => {
    expect(noAppNativo()).toBe(false)
  })

  it('transforma os links do app no caminho da tela', () => {
    expect(caminhoDoLink('condomix://convite?codigo=abc')).toBe('/convite?codigo=abc')
    expect(caminhoDoLink('condomix://debitos')).toBe('/debitos')
    expect(caminhoDoLink('https://app.condomix.com.br/reservas')).toBe('/reservas')
    expect(caminhoDoLink('mailto:ana@teste.com')).toBeNull()
    expect(caminhoDoLink('isso não é link')).toBeNull()
  })

  it('guarda o documento com um nome que o aparelho aceita', () => {
    expect(nomeSeguro('Ata de assembleia.pdf')).toBe('Ata de assembleia.pdf')
    expect(nomeSeguro('../contas 01/2026?.pdf')).toBe('.._contas 01_2026_.pdf')
    expect(nomeSeguro('  ')).toBe('documento')
  })
})
