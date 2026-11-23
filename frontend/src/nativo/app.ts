import { App as AppNativo } from '@capacitor/app'
import { Browser } from '@capacitor/browser'
import { Capacitor } from '@capacitor/core'
import { StatusBar, Style } from '@capacitor/status-bar'

/** Só no app instalado (iPhone ou Android); no navegador e no PWA, falso. */
export function noAppNativo(): boolean {
  return Capacitor.isNativePlatform()
}

/**
 * Abre um endereço de fora do app (PDF do boleto, site do gateway): no app nativo, no navegador do sistema por
 * cima do app; no site, numa aba nova.
 */
export async function abrirForaDoApp(endereco: string): Promise<void> {
  if (noAppNativo()) {
    await Browser.open({ url: endereco })
  } else {
    window.open(endereco, '_blank', 'noopener')
  }
}

/**
 * Abre um arquivo que o app já baixou (ex.: documento, que exige o token).
 * - No app nativo, o WebView não baixa nem abre `blob:`: o arquivo vai para o cache do aparelho e abre na
 *   pré-visualização do sistema (no iPhone, com o botão de compartilhar e salvar em Arquivos).
 * - No navegador, abre a partir da memória (aba nova ou download, conforme o tipo).
 */
export async function abrirArquivo(arquivo: Blob, nome: string): Promise<void> {
  if (noAppNativo()) {
    const { Directory, Filesystem } = await import('@capacitor/filesystem')
    const { FileOpener } = await import('@capacitor-community/file-opener')
    const salvo = await Filesystem.writeFile({
      path: nomeSeguro(nome),
      data: await emBase64(arquivo),
      directory: Directory.Cache,
    })
    await FileOpener.open({ filePath: salvo.uri, contentType: arquivo.type || undefined })
    return
  }
  const endereco = URL.createObjectURL(arquivo)
  const link = document.createElement('a')
  link.href = endereco
  link.target = '_blank'
  link.rel = 'noopener'
  link.download = nome
  link.click()
  setTimeout(() => URL.revokeObjectURL(endereco), 60_000)
}

/** Nome de arquivo sem barras nem caracteres que o sistema de arquivos recusa. */
export function nomeSeguro(nome: string): string {
  const limpo = [...nome].map((c) => (c < ' ' || '/\\:*?"<>|'.includes(c) ? '_' : c)).join('').trim()
  return limpo || 'documento'
}

function emBase64(arquivo: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const leitor = new FileReader()
    leitor.onload = () => resolve(String(leitor.result).split(',')[1] ?? '')
    leitor.onerror = () => reject(leitor.error)
    leitor.readAsDataURL(arquivo)
  })
}

/** As telas iniciais: nelas, o "voltar" do Android fecha o app em vez de voltar. */
const TELAS_INICIAIS = ['/', '/entrar', '/plataforma', '/condominio']

/**
 * Prepara o app nativo: barra de status, botão "voltar" do Android e os links condomix:// (abrem a tela certa).
 * @param navegar navega dentro do app (o do React Router)
 */
export function prepararAppNativo(navegar: (caminho: string) => void): () => void {
  if (!noAppNativo()) return () => {}
  StatusBar.setStyle({ style: Style.Dark }).catch(() => {})
  if (Capacitor.getPlatform() === 'android') {
    StatusBar.setBackgroundColor({ color: '#0f766e' }).catch(() => {})
  }
  const voltar = AppNativo.addListener('backButton', ({ canGoBack }) => {
    if (!canGoBack || TELAS_INICIAIS.includes(window.location.pathname)) {
      AppNativo.exitApp()
    } else {
      window.history.back()
    }
  })
  const link = AppNativo.addListener('appUrlOpen', ({ url }) => {
    const caminho = caminhoDoLink(url)
    if (caminho) navegar(caminho)
  })
  return () => {
    voltar.then((l) => l.remove())
    link.then((l) => l.remove())
  }
}

/**
 * "condomix://convite?codigo=x" ou "https://app.condomix.com.br/debitos" → "/convite?codigo=x", "/debitos".
 * Vazio se não for um link do app.
 */
export function caminhoDoLink(url: string): string | null {
  try {
    const u = new URL(url)
    if (u.protocol === 'condomix:') {
      // Em condomix://convite, "convite" vem como host.
      return `/${u.host}${u.pathname === '/' ? '' : u.pathname}${u.search}`.replace(/\/+$/, '') || '/'
    }
    if (u.protocol === 'https:') return `${u.pathname}${u.search}`
    return null
  } catch {
    return null
  }
}
