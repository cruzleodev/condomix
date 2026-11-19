/* Push do Condomix: carregado pelo service worker do PWA (vite.config.ts → workbox.importScripts). */
self.addEventListener('push', (evento) => {
  let dados = {}
  try {
    dados = evento.data ? evento.data.json() : {}
  } catch {
    dados = { titulo: 'Condomix', texto: evento.data ? evento.data.text() : '' }
  }
  evento.waitUntil(self.registration.showNotification(dados.titulo || 'Condomix', {
    body: dados.texto || '',
    icon: '/icone-192.png',
    badge: '/icone-192.png',
    tag: dados.id,
    data: { link: dados.link || '/' },
  }))
})

/* Tocar na notificação abre o app na tela certa (ou traz a janela aberta para a frente). */
self.addEventListener('notificationclick', (evento) => {
  evento.notification.close()
  const link = (evento.notification.data && evento.notification.data.link) || '/'
  evento.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((janelas) => {
    for (const janela of janelas) {
      if ('focus' in janela) {
        janela.navigate(link)
        return janela.focus()
      }
    }
    return self.clients.openWindow(link)
  }))
})
