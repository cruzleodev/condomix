# Condomix — instruções para o Claude

Sistema web de gestão de condomínios, vendido como SaaS. Backend em Java 25, Spring Boot 4.1,
Spring Modulith, PostgreSQL 18 e Flyway. Frontend em React + TypeScript (PWA), na pasta `frontend/` deste
mesmo repositório. Visão geral, decisões e etapas: [docs/README.md](docs/README.md).

## Como as etapas são entregues: fatias verticais
Cada etapa entrega uma funcionalidade **de ponta a ponta**: banco → API → **tela**, num único PR.
Nada de "todo o backend primeiro, telas no final": a tela revela problemas na API cedo, quando ainda
são baratos de corrigir, e o usuário consegue testar de verdade.
- Ao criar ou mudar um endpoint, atualize os tipos do frontend (`npm run api:tipos`) e a tela que o usa.
- Uma etapa só é concluída com a tela funcionando no navegador, no tamanho de celular.

Fale sempre em **português** com o usuário, e escreva o código e a documentação em português.

## Comandos

```bash
./mvnw test              # todos os testes do backend (precisa do Docker rodando)
./mvnw spring-boot:run   # roda a API (perfil dev); o Postgres sobe sozinho pelo compose.yaml
cd frontend && npm run dev        # app em http://localhost:5173 (a API precisa estar rodando)
cd frontend && npm test           # testes do frontend
cd frontend && npm run api:tipos  # gera os tipos TypeScript a partir do OpenAPI da API rodando
```

- Swagger: http://localhost:8080/swagger-ui/index.html
- Logins de dev: `sindico@condomix.dev` e `morador@condomix.dev`, senha `condomix123`.

---

## SOLID — regra principal de design

Aplique os cinco princípios em **cerca de 90% do código**. Os outros 10% são os casos listados em
"Quando não aplicar": ali, a simplicidade vale mais que a abstração.
Antes de entregar uma mudança, confira cada princípio abaixo.

### S — Responsabilidade única
Cada classe tem **um único motivo para mudar**.

| Camada | Faz | Não faz |
|---|---|---|
| Controller | Recebe HTTP, valida o pedido (`@Valid`), converte e delega | Regra de negócio, acesso a repositório |
| Serviço (caso de uso) | Coordena um caso de uso ou uma área pequena e coesa | Montar JSON, saber de HTTP |
| Entidade | Protege as próprias regras (construtor com campos obrigatórios, métodos com nome de negócio) | Acessar banco ou outros serviços |
| Repositório | Acesso a dados | Regra de negócio |

- Sinais de que é preciso dividir: a classe passa de ~200 linhas, tem mais de ~5 dependências no
  construtor, ou o nome tem "E", "Gerenciador", "Util" ou "Helper".
- Um serviço por caso de uso quando o módulo crescer (`EmitirCobranca`, `ReservarEspaco`), em vez de
  um `FinanceiroService` com tudo.

### O — Aberto/fechado
Para acrescentar um comportamento, crie uma **nova implementação** em vez de editar `if`/`switch` existentes.

- Variações previstas no projeto, cada uma atrás de uma interface:
  - **Gateway de pagamento:** `GatewayDePagamento`, com implementação Asaas; outras virão.
  - **Canais de notificação:** `CanalDeNotificacao` (WhatsApp, e-mail, push).
  - **Regras de reserva:** `RegraDeReserva` (antecedência, limite por mês, inadimplência...). O
    serviço recebe uma `List<RegraDeReserva>` e aplica todas.
  - **Armazenamento de arquivos:** `ArmazenamentoDeArquivos` (disco local em dev, S3 em produção).
- Nada de `switch` sobre enum para escolher comportamento. Coloque o comportamento no próprio enum
  ou use uma estratégia.

### L — Substituição de Liskov
Qualquer implementação tem que funcionar no lugar da interface **sem que quem chama perceba**.

- Nunca lance `UnsupportedOperationException` para "não implementado".
- Não exija mais do que a interface pede (pré-condições) nem entregue menos (pós-condições).
- Quando houver várias implementações, escreva um **teste de contrato** (classe abstrata de teste)
  e rode-o contra todas elas.
- Prefira composição a herança. Herança de entidade só com motivo forte.

### I — Segregação de interfaces
Interfaces **pequenas, pensadas para quem usa**.

- Separe leitura de escrita quando os consumidores forem diferentes, por exemplo
  `ConsultaDeCobrancas` e `EmissaoDeCobrancas`.
- **Entre módulos**, exponha uma interface ou serviço com só o que o outro módulo precisa.
  Nunca exponha um `JpaRepository` para outro módulo: ele carrega dezenas de métodos (inclusive `delete`).
- Os DTOs da API são `record`s com só os campos que a tela usa. Não devolva entidades no JSON.

### D — Inversão de dependência
O código de negócio depende de **abstrações do próprio módulo**. A infraestrutura implementa essas abstrações.

- Serviços externos (gateway, WhatsApp, e-mail, armazenamento) ficam atrás de uma interface
  declarada no módulo que usa. A implementação fica num subpacote interno (ex.: `financeiro/asaas`).
- **Injeção sempre pelo construtor.** Nada de `@Autowired` em campo no código principal (nos testes, tudo bem).
- Serviço não dá `new` em colaborador. Recebe pelo construtor.
- Tempo: injete `java.time.Clock` em vez de chamar `Instant.now()` direto onde a regra depender de data/hora.
  Isso permite testar vencimentos e prazos.

### Quando não aplicar (os 10%)
- **Não crie interface com uma única implementação** se ela não for uma fronteira, ou seja, se não
  for sistema externo, outro módulo ou infraestrutura. Um serviço interno simples pode ser uma classe concreta.
- CRUD simples, `record`s de DTO, enums, classes de configuração e testes não precisam de camadas extras.
- Não antecipe variações que não estão no roteiro (YAGNI).
- Ao desviar de propósito, deixe um comentário curto explicando o motivo.

---

## Módulos (Spring Modulith)

`compartilhado`, `plataforma`, `acesso`, `administracao`, `documentos`, `financeiro`, `reservas`, `comunicacao`.

- `administracao` fica acima de `plataforma` e `acesso` (painel do dono do Condomix, `/api/plataforma/**`).

- Só as classes públicas do **pacote raiz** de um módulo podem ser usadas por outro.
  O que for interno vai em subpacotes. O teste `ModularityTests` falha se essa regra for quebrada.
- Prefira **eventos** (`ApplicationEventPublisher` + `@ApplicationModuleListener`) quando um módulo
  só precisa avisar outro que algo aconteceu (ex.: "pagamento confirmado" → comunicação envia WhatsApp).
  Dentro do mesmo módulo, quando a reação precisa ser na mesma transação (tudo ou nada), use `@EventListener`
  (ex.: pagamento de cobrança → lançamentos no caixa).
- Sem dependência circular entre módulos. `compartilhado` não depende de ninguém.

## Multi-tenant — regras obrigatórias

- Toda tabela com dados de um condomínio tem `tenant_id` (FK para `condominio`, com índice), e a
  entidade marca o campo com `@TenantId`. O Hibernate filtra sozinho.
- O tenant vem de `TenantAtual`. O Hibernate lê o tenant **no início da transação**: `executarComo`
  e `comoSistema` envolvem a transação, **nunca** são chamados dentro de um `@Transactional`.
- **Consultas em SQL puro (`nativeQuery = true`) NÃO passam pelo filtro do `@TenantId`.** Nelas, escreva
  `tenant_id = :tenantId` (com `TenantAtual.obrigatorio()`) e, se a entidade usar `@SQLRestriction`, repita
  a condição também. Prefira JPQL; use SQL puro só quando necessário (ex.: busca full-text).
- Cada `Perfil` tem uma origem: `MANDATO`, `PERFIL` (gravado em `perfil_acesso`) ou `PLATAFORMA`.
- **Cargos eleitos (SINDICO, SUBSINDICO, CONSELHEIRO) vêm só de mandatos vigentes** (`Mandato`), nunca de
  `PerfilAcesso`. Para checar permissão, use os papéis do token (`hasRole`), que já juntam as duas fontes.
- `TenantAtual.comoSistema` só serve para leituras internas (ex.: login) e para o administrador da
  plataforma ler **dados da plataforma** (condomínio, plano, assinatura). Nunca para dados internos dos
  condomínios (documentos, pessoas, finanças): para isso existe o modo suporte, auditado.
- Toda funcionalidade nova com dados de condomínio ganha um teste provando que outro condomínio
  não enxerga esses dados.

## Convenções

- Nomes de classes, métodos e campos em português (`Cobranca`, `emitir`, `dataVencimento`).
- Entidades sem setters públicos. Construtor protegido para o JPA e um construtor com os campos
  obrigatórios. As mudanças acontecem por métodos com nome de negócio (`cancelar`, `desativar`).
- IDs `UUID` v7 (`@UuidGenerator(style = VERSION_7)`). Datas e horas em `Instant`/`timestamptz`.
  Valores em dinheiro em `BigDecimal`/`numeric`.
- `@Transactional` fica nos serviços, nunca nos controllers.
- Permissões: use as constantes de `Permissoes` (`GESTAO`, `CONSULTA_DO_CADASTRO`) nos `@PreAuthorize`.
- CPF/CNPJ e telefone: sempre por `DocumentoBrasileiro` e `TelefoneBrasileiro` (aceitam o CNPJ alfanumérico).
- "Meus dados" (minhas unidades, meus boletos...): pelos vínculos da **pessoa** ligada ao login, nunca pelo perfil.
- Erros da API como `ErrorResponseException` (formato RFC 9457). Para os casos comuns, use
  `NaoEncontradoException` (404) e `RegraDeNegocioException` (400/409) do `compartilhado`.
- **Banco:**
  - O Hibernate só valida o esquema; quem cria e altera tabelas é o Flyway.
  - Uma migração nunca é editada depois de commitada: crie uma nova `V{n}__descricao.sql`.
  - Enums ficam como `varchar` com `CHECK`.
  - Filtros opcionais em consultas: use `Specification` (o PostgreSQL recusa `:param is null` com valor nulo sem tipo).
- **Dinheiro:** `BigDecimal` com 2 casas (`Dinheiro.reais`). Ao dividir um total entre unidades, use
  `DistribuicaoDeCentavos` para a soma fechar no centavo. Multa e juros são calculados na data, não gravados.

## Frontend (`frontend/`)

- React + TypeScript + Vite, Tailwind, React Router, TanStack Query. Detalhes em `frontend/README.md`.
- **Tipos da API sempre gerados** (`npm run api:tipos`), nunca escritos à mão.
- **Records da API com nomes únicos** em todo o backend: o OpenAPI usa o nome simples do record como nome do
  tipo (o `ContratoDaApiTests` garante).
- Uma tela por arquivo em `src/paginas`. Componentes visuais em `src/componentes`. Formatação brasileira em `src/util/formato.ts`.
- Pensado primeiro para o celular (375 px). Textos para o usuário em português, sem códigos internos (use rótulos).
- Verifique no navegador, em tamanho de celular, antes de concluir uma etapa.

## Testes

- Toda funcionalidade vem com testes. Testes de integração usam Testcontainers e `@ActiveProfiles("test")`.
- Testes que mexem com dados de condomínio **não** usam `@Transactional` (ver multi-tenant).
  Cada teste cria os próprios dados, com e-mails e nomes únicos.
- Use o `CenarioDeTeste` (`@Import`) para montar condomínio, usuário, perfil, mandato e login.
- Frontend: Vitest + Testing Library (`cd frontend && npm test`), para regras de tela e fluxos importantes.
- Rode `./mvnw test` e `cd frontend && npm test` antes de qualquer commit.

## Git e documentação

### Branches de ambiente
| Branch | Ambiente | Recebe código de |
|---|---|---|
| `desenvolvimento` | Integração das etapas | PR de uma `feature/*` |
| `homologacao` | Testes de aceite antes de publicar | PR de `desenvolvimento` |
| `producao` | O que está no ar | PR de `homologacao` |

- Nunca faça commit direto nessas três branches: tudo entra por pull request.
- Só abra PR para `homologacao` ou `producao` quando o usuário pedir. A `main` ficou só com o commit inicial.
- Uma branch `feature/*` por etapa, criada a partir de `desenvolvimento`. Se o PR da etapa
  anterior ainda não foi aceito, crie a partir da `feature/*` anterior.

### Commits
- **Um commit por etapa.** Ajustes durante a etapa entram no mesmo commit (`git commit --amend`)
  ou são unidos antes do PR. Depois do amend, envie com `git push --force-with-lease`, que só é
  permitido na própria `feature/*`.
- Mensagens de commit em português: um título curto, e no corpo a lista do que mudou.
- Ao terminar uma etapa, crie `docs/etapas/NN-nome.md` (objetivo, o que foi feito, decisões e
  porquê, como verificar, pendências) e atualize a tabela em `docs/README.md`.
- O remoto do git se chama `main` (não `origin`). Para push use o `gh` como credencial:
  `git -c credential.helper= -c 'credential.helper=!gh auth git-credential' push`.
