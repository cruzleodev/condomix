# Condomix — frontend (PWA)

App do Condomix para celular (e computador), em React + TypeScript. Instala pelo navegador do celular
como um app (PWA) e, mais tarde, vai para as lojas com o Capacitor.

## Rodando

Com a API rodando (`./mvnw spring-boot:run` na raiz do projeto):

```bash
cd frontend
npm install        # só na primeira vez
npm run dev        # http://localhost:5173
```

O Vite repassa `/api` para a API em `http://localhost:8080`. Para usar outra porta:
`API_URL=http://localhost:8081 npm run dev`.

## Comandos

| Comando | O que faz |
|---|---|
| `npm run dev` | App em desenvolvimento, com recarga instantânea |
| `npm test` | Testes (Vitest + Testing Library) |
| `npm run build` | Versão de produção em `dist/` (com o service worker do PWA) |
| `npm run lint` | Verificação de código (oxlint) |
| `npm run api:tipos` | Gera `src/api/schema.d.ts` a partir do OpenAPI da API rodando |

## App nativo (iPhone e Android)

O mesmo frontend vira app nativo pelo [Capacitor](https://capacitorjs.com) (`capacitor.config.ts`, pastas `ios/` e
`android/`). Precisa do **Xcode** (iPhone) e do **Android Studio** (Android).

| Comando | O que faz |
|---|---|
| `npm run app:ios:dev` | Abre o app apontando para o Vite deste Mac pela rede local, com atualização na hora. A API precisa estar rodando |
| `npm run app:android:dev` | O mesmo, no Android |
| `npm run app:build` | Build do app para publicar: as telas vão dentro do app e a API é a de `VITE_API_URL` (`.env.app`) |
| `npm run app:ios` / `npm run app:android` | Build e abre o projeto no Xcode / Android Studio |

### Instalar no seu iPhone sem pagar (Apple ID gratuito)
1. Ligue o iPhone no Mac pelo cabo e toque em **Confiar** no iPhone.
2. No iPhone, ligue o **Modo de Desenvolvedor**: **Ajustes → Privacidade e Segurança → Modo de Desenvolvedor** → ligar
   (o iPhone reinicia). A opção só aparece depois de ligar o iPhone no Mac com o Xcode aberto.
3. No Mac, com o iPhone e o Mac no **mesmo Wi-Fi** e a API rodando (`./mvnw spring-boot:run`), rode
   `npm run app:ios:dev`. O Xcode abre sozinho.
4. No Xcode, no painel da esquerda, clique em **App** (o ícone azul do projeto) → aba **Signing & Capabilities** →
   em **Team**, escolha **Add an Account…** e entre com o seu Apple ID; depois escolha **Seu Nome (Personal Team)**.
   Se o Xcode reclamar do identificador, troque o **Bundle Identifier** para algo só seu (ex.: `br.com.condomix.app.leonardo`).
5. No alto do Xcode, escolha o seu iPhone como destino e clique em **▶ (Run)**.
6. Na primeira vez, o iPhone avisa que o desenvolvedor não é confiável: **Ajustes → Geral → VPN e Gerenciamento de
   Dispositivo** → toque no seu Apple ID → **Confiar**. Rode de novo pelo Xcode.

Se o Xcode der **"Command CodeSign failed"**, rode de novo e, na janela do Chaveiro, digite a senha do Mac e clique em
**Sempre Permitir**. Se ele ficar em **"needs to be unlocked"**, deixe o iPhone desbloqueado até o app abrir.

Depois de instalado, o app funciona **sem o cabo**, com o iPhone no mesmo Wi-Fi do Mac (as telas vêm do Vite). Para
instalar de novo sem o cabo: no Xcode, **Window → Devices and Simulators** → o seu iPhone → **Connect via network**.

Com a conta gratuita, o app **vale 7 dias** (depois, é só rodar de novo pelo Xcode), fica só no seu aparelho e **não
recebe push nativo** (a Apple só libera com a conta paga). O push do app instalado pela tela inicial (Safari →
Compartilhar → Adicionar à Tela de Início) funciona sem pagar, com o app publicado em https.

## Organização

| Pasta | Conteúdo |
|---|---|
| `src/api` | Cliente da API tipado (`openapi-fetch`) e os tipos gerados do OpenAPI |
| `src/sessao` | Login, escolha de condomínio, token guardado no aparelho e papéis |
| `src/layout` | Layout do app (cabeçalho e navegação embaixo) e as rotas protegidas |
| `src/paginas` | Uma tela por arquivo |
| `src/componentes` | Botões, campos, cartões, mensagens |
| `src/util` | Formatação brasileira (R$, datas, competência) |

## Regras

- **Tipos da API sempre gerados**, nunca escritos à mão: depois de mudar um endpoint, rode `npm run api:tipos`
  com a API no ar. Se o backend mudar algo que a tela usa, o TypeScript acusa.
- **Primeiro o celular:** toda tela é pensada para 375 px de largura.
- **Dados do servidor** com TanStack Query (cache, carregando, erro). A API nunca vai para o cache do PWA.
- **Mensagens de erro** vêm do campo `detail` da API (formato RFC 9457).
