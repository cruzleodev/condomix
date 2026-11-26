import { useEffect, useRef } from 'react'
import { dinheiroNoCampo, filtrarDecimal, mascaraDeDinheiro } from '../util/formato'
import { Campo } from './ui'

interface Comum {
  rotulo: string
  valor: string
  aoMudar: (texto: string) => void
  required?: boolean
  ajuda?: string
  placeholder?: string
}

/**
 * Valor em reais: aceita só números e formata sozinho ("1.234,56"), preenchendo a partir dos centavos.
 * O texto entregue é lido por `lerReais`.
 */
export function CampoDinheiro({ rotulo, valor, aoMudar, ...resto }: Comum) {
  return (
    <Campo rotulo={rotulo} inputMode="numeric" autoComplete="off" placeholder="0,00" {...resto}
      value={dinheiroNoCampo(valor)} onChange={(e) => aoMudar(mascaraDeDinheiro(e.target.value))} />
  )
}

/**
 * Número digitado: inteiro (dias, pessoas, dia do mês) ou com vírgula (percentual, fração ideal).
 * Letras e outros símbolos não entram. Fora de `min`/`max`, o formulário não é enviado e o motivo aparece.
 */
export function CampoNumero({ rotulo, valor, aoMudar, casas = 0, min, max, ...resto }: Comum & { casas?: number; min?: number; max?: number }) {
  const campo = useRef<HTMLInputElement>(null)
  const numero = valor === '' ? undefined : Number(valor.replace(',', '.'))
  const problema = numero === undefined ? null
    : min !== undefined && numero < min ? `O mínimo é ${String(min).replace('.', ',')}.`
      : max !== undefined && numero > max ? `O máximo é ${String(max).replace('.', ',')}.` : null
  useEffect(() => { campo.current?.setCustomValidity(problema ?? '') }, [problema])
  return (
    <div>
      <Campo ref={campo} rotulo={rotulo} inputMode={casas > 0 ? 'decimal' : 'numeric'} autoComplete="off" {...resto}
        value={casas > 0 ? valor.replace('.', ',') : valor} onChange={(e) => aoMudar(casas > 0 ? filtrarDecimal(e.target.value, casas) : e.target.value.replace(/\D/g, ''))}
        aria-invalid={problema ? true : undefined} ajuda={problema ? undefined : resto.ajuda} />
      {problema && <span className="mt-1 block text-xs text-red-700">{problema}</span>}
    </div>
  )
}
