// Roda o app nativo apontando para o Vite deste Mac pela rede local, com atualização na hora.
// Uso: npm run app:ios:dev   (ou app:android:dev)
// O iPhone (ou o simulador) e o Mac precisam estar na mesma rede Wi-Fi; a API precisa estar rodando.
import { execSync, spawn } from 'node:child_process'
import { networkInterfaces } from 'node:os'

const plataforma = process.argv[2] ?? 'ios'
const ip = Object.values(networkInterfaces()).flat()
  .find((i) => i && i.family === 'IPv4' && !i.internal && !i.address.startsWith('169.254'))?.address
if (!ip) {
  console.error('Não achei o IP deste Mac na rede local. Conecte o Wi-Fi e tente de novo.')
  process.exit(1)
}
const servidor = `http://${ip}:5173`
console.log(`O app vai abrir ${servidor} (o Vite deste Mac).`)
const vite = spawn('npx', ['vite', '--host'], { stdio: 'inherit' })
process.on('exit', () => vite.kill())
execSync(`npx cap sync ${plataforma}`, { stdio: 'inherit', env: { ...process.env, CAP_SERVIDOR: servidor } })
execSync(`npx cap open ${plataforma}`, { stdio: 'inherit', env: { ...process.env, CAP_SERVIDOR: servidor } })
console.log('No Xcode (ou Android Studio), escolha o aparelho e clique em Run. Ctrl+C aqui encerra o Vite.')
