import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './App'
import { ErroDaApi } from './api/cliente'
import './index.css'
import { SessaoProvider } from './sessao/SessaoProvider'

const consultas = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      // Não insiste em erros de regra (4xx): só em falhas de rede ou do servidor.
      retry: (tentativas, erro) => !(erro instanceof ErroDaApi && erro.status < 500) && tentativas < 2,
    },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={consultas}>
      <SessaoProvider>
        <App />
      </SessaoProvider>
    </QueryClientProvider>
  </StrictMode>,
)
