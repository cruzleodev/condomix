/** Liga e desliga o push neste aparelho (navegador ou app instalado). */

export type SituacaoDoPush = 'sem-suporte' | 'sem-app' | 'bloqueado' | 'desligado' | 'ligado'

/** A chave VAPID em Base64 URL vira os bytes que o navegador pede. */
export function chaveEmBytes(base64Url: string): Uint8Array<ArrayBuffer> {
  const base64 = (base64Url + '='.repeat((4 - (base64Url.length % 4)) % 4)).replace(/-/g, '+').replace(/_/g, '/')
  const texto = atob(base64)
  const bytes = new Uint8Array(new ArrayBuffer(texto.length))
  for (let i = 0; i < texto.length; i++) bytes[i] = texto.charCodeAt(i)
  return bytes
}

/** O service worker do app, se estiver registrado (em desenvolvimento, normalmente não está). */
async function registro(): Promise<ServiceWorkerRegistration | undefined> {
  if (!('serviceWorker' in navigator)) return undefined
  return navigator.serviceWorker.getRegistration()
}

export async function situacaoDoPush(): Promise<SituacaoDoPush> {
  if (typeof window === 'undefined' || !('Notification' in window) || !('PushManager' in window)) return 'sem-suporte'
  const reg = await registro()
  if (!reg) return 'sem-app'
  if (Notification.permission === 'denied') return 'bloqueado'
  return (await reg.pushManager.getSubscription()) ? 'ligado' : 'desligado'
}

/** Pede a permissão, inscreve o aparelho e devolve os dados para guardar no servidor. */
export async function inscrever(chavePublica: string): Promise<{ endpoint: string; p256dh: string; auth: string }> {
  const reg = await registro()
  if (!reg) throw new Error('O push só funciona com o app instalado ou aberto na versão publicada.')
  if ((await Notification.requestPermission()) !== 'granted') {
    throw new Error('As notificações estão bloqueadas neste navegador. Libere nas configurações do site.')
  }
  const inscricao = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: chaveEmBytes(chavePublica) })
  const json = inscricao.toJSON()
  return { endpoint: inscricao.endpoint, p256dh: json.keys?.p256dh ?? '', auth: json.keys?.auth ?? '' }
}

/** Cancela a inscrição deste aparelho e devolve o endereço, para apagar no servidor. */
export async function desinscrever(): Promise<string | undefined> {
  const inscricao = await (await registro())?.pushManager.getSubscription()
  if (!inscricao) return undefined
  await inscricao.unsubscribe()
  return inscricao.endpoint
}
