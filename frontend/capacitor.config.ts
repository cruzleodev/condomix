import type { CapacitorConfig } from '@capacitor/cli'

/**
 * O app nativo (iPhone e Android) com o mesmo frontend. Dois jeitos de rodar:
 * - publicado (padrão): o app leva o site pronto (pasta dist, "npm run app:build") e chama a API pelo
 *   endereço público (VITE_API_URL);
 * - desenvolvimento ("npm run app:ios:dev"): o app abre o Vite do seu Mac pela rede local (CAP_SERVIDOR),
 *   que repassa /api para o backend. Muda o código e o app atualiza na hora.
 */
const servidorDeDesenvolvimento = process.env.CAP_SERVIDOR

const config: CapacitorConfig = {
  appId: 'br.com.condomix.app',
  appName: 'Condomix',
  webDir: 'dist',
  backgroundColor: '#0f766e',
  server: servidorDeDesenvolvimento
    ? { url: servidorDeDesenvolvimento, cleartext: true }
    : { androidScheme: 'https' },
  plugins: {
    SplashScreen: { launchShowDuration: 800, backgroundColor: '#0f766e', showSpinner: false },
  },
}

export default config
