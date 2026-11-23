/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { VitePWA } from 'vite-plugin-pwa'

// Em desenvolvimento, /api vai para o backend (Spring Boot na porta 8080): o navegador vê tudo
// na mesma origem e não é preciso liberar CORS.
export default defineConfig(({ mode }) => ({
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      // No app nativo (modo "app"), o próprio app faz o papel do PWA: sem service worker.
      disable: mode === 'app',
      registerType: 'autoUpdate',
      includeAssets: ['icone.svg'],
      manifest: {
        name: 'Condomix',
        short_name: 'Condomix',
        description: 'Gestão de condomínios: documentos, cobranças e prestação de contas.',
        lang: 'pt-BR',
        theme_color: '#0f766e',
        background_color: '#f8fafc',
        display: 'standalone',
        start_url: '/',
        icons: [
          { src: 'icone-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icone-512.png', sizes: '512x512', type: 'image/png' },
          { src: 'icone-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        // A API nunca vai para o cache: dados financeiros têm que estar sempre atualizados.
        navigateFallbackDenylist: [/^\/api\//],
        // Recebe as notificações push e abre o app na tela certa ao tocar nelas.
        importScripts: ['sw-push.js'],
      },
    }),
  ],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.API_URL ?? 'http://localhost:8080',
        changeOrigin: true,
        // Para a API, a chamada vem do próprio site: sem o Origin, ela não confunde com o app instalado (CORS).
        configure: (proxy) => proxy.on('proxyReq', (pedido) => pedido.removeHeader('origin')),
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/testes/preparacao.ts'],
    css: false,
  },
}))
