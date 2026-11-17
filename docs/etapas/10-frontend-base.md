# Etapa 10 — Frontend parte 1: base do app, login e telas do morador

- **Branch:** `feature/frontend-base` (criada a partir de `desenvolvimento`)
- **Concluída em:** 17/11/2026

## Por que agora

As boas práticas (fatias verticais, *walking skeleton*, feedback rápido) recomendam levar a tela junto
com o backend, e não deixá-la para o final. As etapas 1 a 9 montaram a base (segurança, cadastro,
financeiro). A partir daqui o produto ganha a tela, e **cada etapa nova entrega backend e tela juntos**.
Essa regra foi registrada no CLAUDE.md.

## Tecnologia

| Item | Escolha |
|---|---|
| Base | React 19 + TypeScript 5.9 + Vite 8 |
| Estilo | Tailwind CSS 4, pensado primeiro para o celular |
| Rotas | React Router |
| Dados do servidor | TanStack Query (cache, carregando, erro) |
| API | `openapi-fetch` com **tipos gerados do OpenAPI** (`openapi-typescript`) |
| App instalável | `vite-plugin-pwa` (manifesto, ícones, service worker) |
| Testes | Vitest + Testing Library |

O código fica na pasta `frontend/` deste repositório (instruções em `frontend/README.md`).

- **TypeScript 5.9, e não 7:** o modelo do Vite veio com o TypeScript 7, mas o gerador de tipos ainda exige o 5.
- **Desenvolvimento sem CORS:** o Vite repassa `/api` para o backend, então o navegador vê tudo na mesma origem.

## Telas

| Tela | O que mostra |
|---|---|
| **Entrar** | E-mail e senha. Com um condomínio só, entra direto; com vários, vai para a escolha |
| **Escolher condomínio** | Para o síndico profissional e a administradora. A troca de token acontece sozinha |
| **Início** | Meus débitos (total e vencidas), minhas unidades, avisos de mandato (para a gestão e o conselho), documentos recentes e o caixa do mês |
| **Débitos** | Em aberto (com multa e juros até hoje) e pagas, com os itens de cada cobrança |
| **Documentos** | Busca em português, filtro por categoria, abrir arquivo. O síndico e a administradora também publicam |
| **Balancete** | Mês a mês: receitas, despesas, resultado, saldo, inadimplência e contas |
| **Conta** | Quem sou, meus papéis, trocar de condomínio e sair |
| **Convite** | O link que o síndico envia (`/convite?codigo=...`): definir a senha ou confirmar o login que já existe |
| **Plataforma** | Aviso para o administrador. O painel dele chega na etapa 11 |

### Sessão
- O token fica guardado no aparelho, então o app continua logado ao reabrir. Ao abrir, um token vencido é descartado.
- Se a API responder 401 (token vencido no meio do uso), o app volta ao login e, depois de entrar, **retorna à tela em que a pessoa estava**.
- **Ao sair por vontade própria,** o próximo login vai para o Início, porque pode ser de outra pessoa.

## O backend também mudou (o benefício da fatia vertical)

Gerar os tipos a partir da API revelou um problema que nenhum teste do backend pegava: **5 nomes de
record repetidos** (`CategoriaResposta`, `Ocupante`, `PedidoCancelamento`, `PedidoEncerramento`,
`Resultado`). O OpenAPI usa o nome simples do record como nome do tipo, então um sobrescrevia o outro, e
a **documentação de vários endpoints estava errada**. Por exemplo, as categorias de documentos apareciam
com os campos das categorias financeiras.

- **Correção:** cada record ganhou um nome único (ex.: `CategoriaDeDocumento`, `CategoriaFinanceiraResposta`,
  `PedidoEncerramentoDeMandato`, `PedidoEncerramentoDeVinculo`). O formato do JSON não mudou.
- **Prevenção:** o teste `ContratoDaApiTests` falha se dois records tiverem o mesmo nome.

## Como verificar

```bash
./mvnw test                  # backend: 80 testes
cd frontend && npm test      # frontend: 9 testes
cd frontend && npm run build
```

Os testes do frontend cobrem a formatação brasileira, a leitura do token e o fluxo de login (um
condomínio, vários condomínios, senha errada).

### No navegador (verificado em tamanho de celular, 375×812)
1. Com a API rodando, `cd frontend && npm run dev` e abra http://localhost:5173.
2. Entre com `sindico@condomix.dev`, que mostra a escolha de condomínio. Escolha o **Residencial Exemplo**: aparece
   o Início com os documentos e o saldo da conta.
3. Em **Documentos**, busque `convencao` (sem acento).
4. Em **Conta → Sair**, entre com `morador@condomix.dev`: entra direto e mostra "A-101 · Proprietário".

Para instalar no celular, o app precisa estar publicado com HTTPS (etapa de publicação). No computador, o
Chrome já oferece "Instalar" com o app em produção (`npm run build && npx vite preview`).

## Pendências para a etapa 11 em diante
- **Telas do síndico:** unidades e moradores, convites, geração da taxa, cobranças e baixa, caixa e mandatos.
- **Painel do administrador** da plataforma.
- **Campos obrigatórios no OpenAPI:** hoje todos aparecem como opcionais nos tipos gerados.
- **Publicação:** servir o frontend com HTTPS e definir a política de segurança de conteúdo (CSP).
