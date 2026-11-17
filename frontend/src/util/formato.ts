const reais = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

/** 1234.5 → "R$ 1.234,50". Vazio vira "—". */
export function moeda(valor: number | null | undefined): string {
  return valor === null || valor === undefined ? '—' : reais.format(valor)
}

/** "2026-12-10" → "10/12/2026", sem passar por fuso horário (datas da API são só dias). */
export function data(iso: string | null | undefined): string {
  if (!iso) return '—'
  const [ano, mes, dia] = iso.slice(0, 10).split('-')
  return `${dia}/${mes}/${ano}`
}

const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez']

/** "2026-12" → "dez/2026". */
export function competencia(anoMes: string | null | undefined): string {
  if (!anoMes) return '—'
  const [ano, mes] = anoMes.split('-')
  return `${MESES[Number(mes) - 1]}/${ano}`
}

/** O mês atual no formato da API: "2026-11". */
export function mesAtual(hoje: Date = new Date()): string {
  return `${hoje.getFullYear()}-${String(hoje.getMonth() + 1).padStart(2, '0')}`
}

const ROTULOS_DE_PERFIL: Record<string, string> = {
  SINDICO: 'Síndico',
  SUBSINDICO: 'Subsíndico',
  CONSELHEIRO: 'Conselheiro',
  ADMINISTRADORA: 'Administradora',
  MORADOR: 'Morador',
  PORTARIA: 'Portaria',
  ADMIN_PLATAFORMA: 'Administrador do Condomix',
  SUPORTE: 'Suporte',
}

export function perfil(codigo: string): string {
  return ROTULOS_DE_PERFIL[codigo] ?? codigo
}

/** "abc" + 2 bytes → "2 KB" etc. */
export function tamanhoDeArquivo(bytes: number | undefined): string {
  if (!bytes) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} MB`
}

const ROTULOS_DE_VINCULO: Record<string, string> = {
  PROPRIETARIO: 'Proprietário',
  INQUILINO: 'Inquilino',
  MORADOR: 'Morador',
  DEPENDENTE: 'Dependente',
}

export function vinculo(codigo: string | undefined): string {
  return codigo ? ROTULOS_DE_VINCULO[codigo] ?? codigo : ''
}
