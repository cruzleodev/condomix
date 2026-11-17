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
