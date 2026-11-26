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

/**
 * Separa a lista de unidades digitada para o cadastro em lote: aceita vírgula, ponto e vírgula,
 * espaço ou uma por linha. "101, 102\n201 202" → ["101", "102", "201", "202"]. Remove repetidas.
 */
export function separarIdentificacoes(texto: string): string[] {
  return [...new Set(texto.split(/[\s,;]+/).map((t) => t.trim()).filter(Boolean))]
}

/** Uma faixa de números, para gerar unidades em sequência: (101, 104) → ["101", "102", "103", "104"]. */
export function faixa(de: number, ate: number): string[] {
  if (!Number.isInteger(de) || !Number.isInteger(ate) || ate < de || ate - de > 500) return []
  return Array.from({ length: ate - de + 1 }, (_, i) => String(de + i))
}

/** "0.025" → "2,5%". */
export function percentual(fracao: number | null | undefined): string {
  if (fracao === null || fracao === undefined) return '—'
  return `${(fracao * 100).toLocaleString('pt-BR', { maximumFractionDigits: 4 })}%`
}

const TIPOS_DE_UNIDADE: Record<string, string> = {
  APARTAMENTO: 'Apartamento', CASA: 'Casa', SALA: 'Sala', LOJA: 'Loja', OUTRO: 'Outro',
}

export function tipoDeUnidade(codigo: string | undefined): string {
  return codigo ? TIPOS_DE_UNIDADE[codigo] ?? codigo : ''
}

/** Opções para selects a partir de um dicionário de rótulos. */
export function opcoes(rotulos: Record<string, string>): { valor: string; rotulo: string }[] {
  return Object.entries(rotulos).map(([valor, rotulo]) => ({ valor, rotulo }))
}

export const ROTULOS = {
  tipoDeUnidade: TIPOS_DE_UNIDADE,
  vinculo: { PROPRIETARIO: 'Proprietário', INQUILINO: 'Inquilino', MORADOR: 'Morador', DEPENDENTE: 'Dependente' },
  cargo: { SINDICO: 'Síndico', SUBSINDICO: 'Subsíndico', CONSELHEIRO: 'Conselheiro' },
  situacaoDoMandato: { FUTURO: 'Futuro', VIGENTE: 'Vigente', A_VENCER: 'A vencer', PRORROGADO: 'Prorrogado', ENCERRADO: 'Encerrado' },
  motivoDeEncerramento: { RENUNCIA: 'Renúncia', DESTITUICAO: 'Destituição', OUTRO: 'Outro' },
  tipoDeRateio: { IGUALITARIO: 'Igual para todas as unidades', FRACAO_IDEAL: 'Pela fração ideal', VALOR_POR_GRUPO: 'Valor fixo por grupo' },
  formaDePagamento: { PIX: 'Pix', BOLETO: 'Boleto', TRANSFERENCIA: 'Transferência', DINHEIRO: 'Dinheiro', CARTAO: 'Cartão', OUTRO: 'Outra' },
  itemAvulso: { MULTA_DISCIPLINAR: 'Multa disciplinar', RESERVA_DE_ESPACO: 'Aluguel de espaço', CONSUMO: 'Consumo (água, gás)', TAXA_EXTRA: 'Taxa extra', OUTRO: 'Outro' },
  statusDaCobranca: { ABERTA: 'Em aberto', PAGA: 'Paga', CANCELADA: 'Cancelada' },
  origemDaCobranca: { GERACAO_MENSAL: 'Taxa do mês', AVULSA: 'Avulsa', IMPORTACAO: 'Importada' },
  tipoDeConta: { CONTA_CORRENTE: 'Conta corrente', POUPANCA: 'Poupança', APLICACAO: 'Aplicação', CAIXA_FISICO: 'Caixa físico' },
  /** Inclui a conta do gateway, que o sistema cria sozinho (não aparece em "Nova conta"). */
  tipoDeContaExibido: { CONTA_CORRENTE: 'Conta corrente', POUPANCA: 'Poupança', APLICACAO: 'Aplicação', CAIXA_FISICO: 'Caixa físico', GATEWAY: 'Conta no gateway (Asaas)' },
  tipoDeCondominio: { RESIDENCIAL_VERTICAL: 'Residencial (prédio)', RESIDENCIAL_HORIZONTAL: 'Residencial (casas)', COMERCIAL: 'Comercial', MISTO: 'Misto' },
  periodicidade: { MENSAL: 'Mensal', SEMESTRAL: 'Semestral', ANUAL: 'Anual' },
  statusDaAssinatura: { TESTE: 'Em teste', ATIVA: 'Ativa', SUSPENSA: 'Suspensa', CANCELADA: 'Cancelada' },
  situacaoDoBoleto: { PENDENTE: 'Gerando boleto', EMITIDO: 'Boleto emitido', FALHOU: 'Boleto com problema', PAGO: 'Pago pelo boleto/Pix', A_CANCELAR: 'Cancelando boleto', CANCELADO: 'Boleto cancelado' },
  gateway: { ASAAS: 'Asaas', SIMULADO: 'Simulado (testes)' },
  ambienteDoGateway: { SANDBOX: 'Sandbox (testes)', PRODUCAO: 'Produção' },
  tipoDeEventoDaAssinatura: {
    CLIENTE_NOVO: 'Entrou como cliente', TROCA_DE_PLANO: 'Trocou o plano', MUDANCA_DE_SITUACAO: 'Mudou a situação',
    SUSPENSAO: 'Acesso suspenso', REATIVACAO: 'Acesso reativado', PRAZO_DO_TESTE: 'Mudou o prazo do teste',
    FIM_DO_TESTE: 'Fim do teste: passou a pagar', SUSPENSAO_POR_FALTA_DE_PAGAMENTO: 'Suspenso por falta de pagamento',
    REATIVACAO_APOS_PAGAMENTO: 'Reativado: fatura paga',
  },
  situacaoDaFatura: { A_EMITIR: 'Gerando boleto', EMITIDA: 'Em aberto', FALHOU: 'Com problema', PAGA: 'Paga', A_CANCELAR: 'Cancelando', CANCELADA: 'Cancelada' },
  motivoDaSuspensao: { MANUAL: 'Suspenso pelo administrador', FALTA_DE_PAGAMENTO: 'Suspenso por falta de pagamento' },
  modulo: { DOCUMENTOS: 'Documentos', FINANCEIRO: 'Financeiro', RESERVAS: 'Reservas', WHATSAPP: 'WhatsApp' },
  tipoDeLancamento: { RECEITA: 'Receita', DESPESA: 'Despesa', TRANSFERENCIA_ENTRADA: 'Transferência (entrada)', TRANSFERENCIA_SAIDA: 'Transferência (saída)' },
} as const

/** "5591999998888" → "(91) 99999-8888". */
export function telefone(e164: string | null | undefined): string {
  if (!e164) return ''
  const local = e164.startsWith('55') ? e164.slice(2) : e164
  if (local.length === 11) return `(${local.slice(0, 2)}) ${local.slice(2, 7)}-${local.slice(7)}`
  if (local.length === 10) return `(${local.slice(0, 2)}) ${local.slice(2, 6)}-${local.slice(6)}`
  return e164
}

/** CPF "52998224725" → "529.982.247-25"; CNPJ (inclusive alfanumérico) → "12.ABC.345/01DE-35". */
export function documento(valor: string | null | undefined): string {
  if (!valor) return ''
  if (valor.length === 11) return `${valor.slice(0, 3)}.${valor.slice(3, 6)}.${valor.slice(6, 9)}-${valor.slice(9)}`
  if (valor.length === 14) return `${valor.slice(0, 2)}.${valor.slice(2, 5)}.${valor.slice(5, 8)}/${valor.slice(8, 12)}-${valor.slice(12)}`
  return valor
}

/** Data de hoje no formato da API: "2026-11-17". */
export function hojeIso(hoje: Date = new Date()): string {
  return `${hoje.getFullYear()}-${String(hoje.getMonth() + 1).padStart(2, '0')}-${String(hoje.getDate()).padStart(2, '0')}`
}

/** Fim de um mandato de 1 ano: "2026-11-17" → "2027-11-16". */
export function umAnoDepoisMenosUmDia(iso: string): string {
  const [ano, mes, dia] = iso.split('-').map(Number)
  const d = new Date(Date.UTC(ano + 1, mes - 1, dia - 1))
  return d.toISOString().slice(0, 10)
}

/** Primeiro e último dia do mês ("2026-11" → ["2026-11-01", "2026-11-30"]). */
export function periodoDoMes(anoMes: string): [string, string] {
  const [ano, mes] = anoMes.split('-').map(Number)
  const ultimo = new Date(Date.UTC(ano, mes, 0)).getUTCDate()
  return [`${anoMes}-01`, `${anoMes}-${String(ultimo).padStart(2, '0')}`]
}

/** O mês seguinte: "2026-12" → "2027-01". */
export function proximoMes(anoMes: string): string {
  const [ano, mes] = anoMes.split('-').map(Number)
  return mes === 12 ? `${ano + 1}-01` : `${ano}-${String(mes + 1).padStart(2, '0')}`
}

/** Período de uma fatura (o fim não entra): ("2026-11-19", "2026-12-19") → "19/11/2026 a 18/12/2026". */
export function periodoDaFatura(inicio: string | null | undefined, fimExclusivo: string | null | undefined): string {
  if (!inicio || !fimExclusivo) return '—'
  const [ano, mes, dia] = fimExclusivo.split('-').map(Number)
  const ultimoDia = new Date(Date.UTC(ano, mes - 1, dia - 1)).toISOString().slice(0, 10)
  return `${data(inicio)} a ${data(ultimoDia)}`
}

/** "18:00:00" → "18h"; "18:30:00" → "18h30". */
export function hora(texto: string | null | undefined): string {
  if (!texto) return ''
  const [h, m] = texto.split(':')
  return m && m !== '00' ? `${Number(h)}h${m}` : `${Number(h)}h`
}

/** Um horário de espaço: "Noite (18h às 23h)". */
export function horario(nome: string | null | undefined, inicio: string | null | undefined, fim: string | null | undefined): string {
  return `${nome ?? ''} (${hora(inicio)} às ${hora(fim)})`
}

const DIAS_DA_SEMANA = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb']

/** "2026-12-20" → "dom, 20/12/2026" (sem fuso: a data da API é só o dia). */
export function diaDaSemana(iso: string | null | undefined): string {
  if (!iso) return '—'
  const [ano, mes, dia] = iso.slice(0, 10).split('-').map(Number)
  return `${DIAS_DA_SEMANA[new Date(Date.UTC(ano, mes - 1, dia)).getUTCDay()]}, ${data(iso)}`
}

/** Soma dias a uma data da API: ("2026-12-30", 3) → "2027-01-02". */
export function somarDias(iso: string, dias: number): string {
  const [ano, mes, dia] = iso.split('-').map(Number)
  return new Date(Date.UTC(ano, mes - 1, dia + dias)).toISOString().slice(0, 10)
}

/** Lê um valor digitado em reais ("1.234,56", "1234.56", "1234,5") → 1234.56. Vazio ou inválido → undefined. */
export function lerReais(texto: string): number | undefined {
  const limpo = texto.replace(/R\$|\s/g, '')
  if (!limpo) return undefined
  const numero = Number(limpo.includes(',') ? limpo.replace(/\./g, '').replace(',', '.') : limpo)
  return Number.isFinite(numero) ? numero : undefined
}

/** "2026-11-19T14:05:00Z" → "19/11/2026 11:05" (no fuso de quem está vendo). */
export function dataHora(iso: string | null | undefined): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }).replace(',', '')
}

/** "2026-11" + 2 → "2027-01". */
export function somarMeses(anoMes: string, meses: number): string {
  const [ano, mes] = anoMes.split('-').map(Number)
  const total = ano * 12 + (mes - 1) + meses
  return `${Math.floor(total / 12)}-${String((total % 12) + 1).padStart(2, '0')}`
}

const MESES_POR_EXTENSO = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro', 'outubro',
  'novembro', 'dezembro']

/** "2026-11" → "novembro de 2026". */
export function mesPorExtenso(anoMes: string): string {
  const [ano, mes] = anoMes.split('-')
  return `${MESES_POR_EXTENSO[Number(mes) - 1]} de ${ano}`
}

/**
 * As semanas de um mês para o calendário (domingo a sábado). Os dias de fora do mês ficam `null`.
 * Ex.: "2026-11" começa num domingo: a primeira semana é 01 a 07.
 */
export function semanasDoMes(anoMes: string): (string | null)[][] {
  const [ano, mes] = anoMes.split('-').map(Number)
  const primeiroDiaDaSemana = new Date(Date.UTC(ano, mes - 1, 1)).getUTCDay()
  const diasNoMes = new Date(Date.UTC(ano, mes, 0)).getUTCDate()
  const celulas: (string | null)[] = Array(primeiroDiaDaSemana).fill(null)
  for (let d = 1; d <= diasNoMes; d++) celulas.push(`${anoMes}-${String(d).padStart(2, '0')}`)
  while (celulas.length % 7) celulas.push(null)
  const semanas: (string | null)[][] = []
  for (let i = 0; i < celulas.length; i += 7) semanas.push(celulas.slice(i, i + 7))
  return semanas
}

/** Só números, com as barras no lugar: "23112026" → "23/11/2026". */
export function mascaraDeData(texto: string): string {
  const numeros = texto.replace(/\D/g, '').slice(0, 8)
  return [numeros.slice(0, 2), numeros.slice(2, 4), numeros.slice(4)].filter(Boolean).join('/')
}

/** "23/11/2026" → "2026-11-23"; vazio se incompleta ou se o dia não existe (ex.: 31/02). */
export function dataParaIso(texto: string): string {
  const partes = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(texto)
  if (!partes) return ''
  const [, dia, mes, ano] = partes
  const d = new Date(Date.UTC(Number(ano), Number(mes) - 1, Number(dia)))
  if (d.getUTCFullYear() !== Number(ano) || d.getUTCMonth() !== Number(mes) - 1 || d.getUTCDate() !== Number(dia)) return ''
  return `${ano}-${mes}-${dia}`
}

/**
 * Máscara de dinheiro: só números, preenchidos a partir dos centavos (como na maquininha).
 * "15000" → "150,00"; "123456" → "1.234,56"; sem números → "".
 */
export function mascaraDeDinheiro(texto: string): string {
  const numeros = texto.replace(/\D/g, '').replace(/^0+/, '').slice(0, 13)
  if (!numeros) return ''
  return (Number(numeros) / 100).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** Mostra no campo de dinheiro um valor guardado em qualquer formato ("150.5", "150,5", "1.234,56"). */
export function dinheiroNoCampo(valor: string): string {
  const numero = lerReais(valor)
  return numero === undefined ? '' : numero.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** Número com vírgula (ex.: percentual): só números e uma vírgula, com até `casas` decimais. "2.5" → "2,5". */
export function filtrarDecimal(texto: string, casas = 2): string {
  const limpo = texto.replace(/\./g, ',').replace(/[^\d,]/g, '')
  const [inteiro, ...resto] = limpo.split(',')
  if (resto.length === 0) return inteiro
  return `${inteiro || '0'},${resto.join('').slice(0, casas)}`
}
