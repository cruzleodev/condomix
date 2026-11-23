# Etapa 19 — App nativo (iPhone e Android), sem publicar ainda

- **Branch:** `feature/app-nativo` (criada a partir de `desenvolvimento`)
- **Concluída em:** 22/11/2026

## Objetivo

O mesmo app vira **app de verdade para iPhone e Android** (Capacitor), pronto para testar no seu aparelho **sem pagar
nada**. A publicação nas lojas vem depois, com mais ajustes e com a API no ar.

## Decisões (tomadas com você no início da etapa)

| Tema | Decisão |
|---|---|
| Nome e identificador | **Condomix**, `br.com.condomix.app` (não muda depois de publicado) |
| Plataformas | **iPhone e Android** juntos (o Android testa quando houver o Android Studio) |
| Excluir conta | **Apaga o login e mantém o histórico**: o login, as notificações e o push somem; o cadastro e as cobranças ficam com o condomínio |
| Custo | **Sem conta paga por ora**: no iPhone, pelo Xcode com o Apple ID gratuito (vale 7 dias, sem push nativo) |

## O que foi feito

### Apps nativos (Capacitor 8)
- Projetos `frontend/ios` e `frontend/android`, com nome, identificador, **ícone** e **tela de abertura** gerados a
  partir do ícone do app (`frontend/assets`). O ícone do iPhone vai sem transparência, como a App Store exige.
- **Dois jeitos de rodar:**
  - **desenvolvimento** (`npm run app:ios:dev`): o app abre o Vite do Mac pela rede local, com atualização na hora;
    o Vite repassa `/api` para o backend;
  - **publicação** (`npm run app:build`): as telas vão dentro do app, e a API é o endereço público (`VITE_API_URL` em
    `frontend/.env.app`).
- No app, o **service worker do PWA fica desligado**: o próprio app faz esse papel.
- **Comportamento de app** (`src/nativo/app.ts`):
  - links `condomix://tela` (ex.: `condomix://convite?codigo=...`) abrem o app na tela certa;
  - o "voltar" do Android volta, e nas telas iniciais fecha o app;
  - a barra de status segue as cores do app;
  - o PDF do boleto abre no navegador do sistema, por cima do app;
  - os **documentos** abrem na pré-visualização do sistema (no iPhone, com o botão de compartilhar e salvar em
    Arquivos). O WebView do app não baixa arquivos como o navegador: o documento vai para o cache do aparelho
    (`@capacitor/filesystem`) e é aberto pelo `@capacitor-community/file-opener`. No site, continua como antes.
- **iPhone:** só a rede local pode usar http (`NSAllowsLocalNetworking`), para o modo de desenvolvimento; a internet
  continua só https. Também tem a declaração de criptografia padrão (`ITSAppUsesNonExemptEncryption`), que dispensa
  essa pergunta na App Store.

### API
- **CORS** só para o app instalado (`capacitor://localhost` no iPhone, `https://localhost` no Android), configurável
  em `CONDOMIX_CORS_ORIGENS`. O site não precisa, porque fica na mesma origem da API.
- O proxy do Vite tira o cabeçalho `Origin`: sem isso, o login no `npm run dev` passava a dar 403 com o CORS ligado.

### Excluir minha conta (exigência da App Store)
- Em **Conta → Excluir minha conta**: explica o que acontece, pede a senha e, confirmado, encerra a sessão.
- `POST /api/auth/exclusao-da-conta`:
  - o login vira um e-mail que não existe, com uma senha que ninguém sabe; os tokens deixam de valer;
  - a pessoa sai de todos os condomínios: o cadastro fica, sem login, e os perfis são desativados;
  - as notificações, os aparelhos do push e as preferências são apagados (evento `ContaExcluida`);
  - o administrador da plataforma não exclui o login pelo app.
- O cadastro e o histórico financeiro ficam com o condomínio, que é o responsável por eles (LGPD).

### Política de privacidade
- Página aberta (`/privacidade`), com links na entrada e em Conta. É um **texto-base**: revisar com um advogado antes
  de publicar nas lojas.

## Como verificar

```bash
./mvnw test                  # backend: 149 testes (ExclusaoDeContaTests: exclusão da conta e CORS)
cd frontend && npm test      # frontend: 39 testes
```

O passo a passo para instalar no seu iPhone com o Apple ID gratuito está no `frontend/README.md`, em "App nativo".

### Verificado no iPhone (iPhone XR, iOS 18.7, Apple ID gratuito, modo de desenvolvimento)
1. Instalado pelo Xcode com o Personal Team (fica gravado no projeto iOS, em `DEVELOPMENT_TEAM`).
2. O app abriu na tela de entrada, pela rede local, e o login funcionou.
3. **Reservas:** reserva feita e mostrada em Minhas reservas.
4. **Documentos:** na primeira tentativa, não abriam (o download do navegador não funciona no app). Corrigido
   como descrito acima; depois, o documento abriu na pré-visualização do iPhone.
5. **Conta:** o botão "Excluir minha conta" aparece.
6. **Link do app:** `condomix://notificacoes`, digitado no Safari, abriu o app direto em Notificações.
7. O **boleto** não foi testado: sem gateway ligado em desenvolvimento, não há PDF de boleto.

### Problemas da primeira instalação (e a solução)
- **"Command CodeSign failed" (`errSecInternalComponent`):** o macOS bloqueou o acesso à chave do certificado. Rodar
  de novo e, na janela do Chaveiro, digitar a senha do Mac e clicar em **Sempre Permitir** (pode aparecer várias vezes).
- **"iPhone needs to be unlocked":** deixar o iPhone desbloqueado, com a tela acesa, até o app abrir.
- **"Desenvolvedor não confiável":** Ajustes → Geral → VPN e Gerenciamento de Dispositivo → Confiar.

## Pendências (para a publicação nas lojas)
- **Conta paga da Apple** (US$ 99/ano) e **Google Play** (US$ 25), para o TestFlight e as lojas.
- **Push nativo** (Firebase), como mais um `CanalDeNotificacao`.
- **Links universais** (`https://app.condomix.com.br/...` abrindo direto no app): exigem a conta paga e o site publicado.
- **API no ar** (homologação ou produção, em https), para o app publicado e para a revisão das lojas.
- **Login de demonstração** para os revisores, capturas de tela e textos das lojas.
- Revisão jurídica da política de privacidade.
- **Testar no Android** (precisa do Android Studio) e o **PDF do boleto** no app, com o gateway ligado.
