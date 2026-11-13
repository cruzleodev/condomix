# Condomix — instruções para o Claude

Sistema web de gestão de condomínios, vendido como SaaS. Backend em Java 25, Spring Boot 4.1,
Spring Modulith, PostgreSQL 18 e Flyway. Visão geral, decisões e etapas: [docs/README.md](docs/README.md).

Fale sempre em **português** com o usuário, e escreva o código e a documentação em português.

## Comandos

```bash
./mvnw test              # todos os testes (precisa do Docker rodando)
./mvnw spring-boot:run   # roda a aplicação (perfil dev); o Postgres sobe sozinho pelo compose.yaml
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

`compartilhado`, `plataforma`, `acesso`, `documentos`, `financeiro`, `reservas`, `comunicacao`.

- Só as classes públicas do **pacote raiz** de um módulo podem ser usadas por outro.
  O que for interno vai em subpacotes. O teste `ModularityTests` falha se essa regra for quebrada.
- Prefira **eventos** (`ApplicationEventPublisher` + `@ApplicationModuleListener`) quando um módulo
  só precisa avisar outro que algo aconteceu (ex.: "pagamento confirmado" → comunicação envia WhatsApp).
- Sem dependência circular entre módulos. `compartilhado` não depende de ninguém.

## Multi-tenant — regras obrigatórias

- Toda tabela com dados de um condomínio tem `tenant_id` (FK para `condominio`, com índice), e a
  entidade marca o campo com `@TenantId`. O Hibernate filtra sozinho.
- O tenant vem de `TenantAtual`. O Hibernate lê o tenant **no início da transação**: `executarComo`
  e `comoSistema` envolvem a transação, **nunca** são chamados dentro de um `@Transactional`.
- `TenantAtual.comoSistema` só serve para rotinas internas de leitura (ex.: login). Nunca use
  para atender pedido de usuário.
- Toda funcionalidade nova com dados de condomínio ganha um teste provando que outro condomínio
  não enxerga esses dados.

## Convenções

- Nomes de classes, métodos e campos em português (`Cobranca`, `emitir`, `dataVencimento`).
- Entidades sem setters públicos. Construtor protegido para o JPA e um construtor com os campos
  obrigatórios. As mudanças acontecem por métodos com nome de negócio (`cancelar`, `desativar`).
- IDs `UUID` v7 (`@UuidGenerator(style = VERSION_7)`). Datas e horas em `Instant`/`timestamptz`.
  Valores em dinheiro em `BigDecimal`/`numeric`.
- `@Transactional` fica nos serviços, nunca nos controllers.
- Erros da API como `ErrorResponseException` (formato RFC 9457).
- **Banco:**
  - O Hibernate só valida o esquema; quem cria e altera tabelas é o Flyway.
  - Uma migração nunca é editada depois de commitada: crie uma nova `V{n}__descricao.sql`.
  - Enums ficam como `varchar` com `CHECK`.

## Testes

- Toda funcionalidade vem com testes. Testes de integração usam Testcontainers e `@ActiveProfiles("test")`.
- Testes que mexem com dados de condomínio **não** usam `@Transactional` (ver multi-tenant).
  Cada teste cria os próprios dados, com e-mails e nomes únicos.
- Rode `./mvnw test` antes de qualquer commit.

## Git e documentação

- `main` sempre estável. Uma branch `feature/*` por etapa, criada a partir da anterior.
- Não abrir PR para a `main` sem o usuário pedir.
- Mensagens de commit em português.
- Ao terminar uma etapa, crie `docs/etapas/NN-nome.md` (objetivo, o que foi feito, decisões e
  porquê, como verificar, pendências) e atualize a tabela em `docs/README.md`.
- O remoto do git se chama `main` (não `origin`). Para push use o `gh` como credencial:
  `git -c credential.helper= -c 'credential.helper=!gh auth git-credential' push`.
