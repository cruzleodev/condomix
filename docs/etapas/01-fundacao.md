# Etapa 1 — Fundação

- **Branch:** `feature/fundacao`
- **Commit:** `8687964`
- **Concluída em:** 13/11/2026

## Objetivo

Deixar o projeto pronto para receber as funcionalidades: dependências definidas, banco subindo
sozinho em desenvolvimento, módulos criados e testes automatizados rodando.

## O que foi feito

### Dependências (`pom.xml`)
- Empacotamento trocado de `war` para `jar`: a aplicação roda com servidor embutido
  (por isso o `ServletInitializer` foi removido).
- Spring Boot 4.1.2 com Java 25.

| Grupo | Dependências |
|---|---|
| API | `webmvc`, `validation`, `springdoc-openapi` (Swagger) |
| Segurança | `security`, `oauth2-resource-server` (para o login com JWT) |
| Banco | `data-jpa`, `flyway`, `flyway-database-postgresql`, driver `postgresql` |
| Módulos | `spring-modulith-starter-core` 2.1.2 |
| Operação | `actuator` (endereço `/actuator/health`) |
| Desenvolvimento | `spring-boot-docker-compose`: sobe o `compose.yaml` ao rodar a aplicação |
| Testes | `testcontainers` (PostgreSQL descartável), `spring-modulith-starter-test` |

### Banco local (`compose.yaml`)
- PostgreSQL 18 (`postgres:18-alpine`), container `condomix-postgres`, porta 5432.
- Banco, usuário e senha: `condomix`.
- Os dados ficam no volume `postgres-data` e sobrevivem entre execuções.
- Com `lifecycle-management: start-only`, o Postgres continua rodando quando a aplicação para.

### Configuração (`application.yaml`)
- Conexão com o banco por variáveis `DB_URL`, `DB_USER` e `DB_PASSWORD`, com os valores locais como padrão.
- `ddl-auto: validate`: o Hibernate **nunca** cria tabelas; só confere se as entidades batem com o banco.
  Quem cria e altera tabelas é o Flyway, pelos arquivos em `src/main/resources/db/migration`.
- `open-in-view: false` e horários gravados em UTC.
- Actuator expõe só `health` e `info`.

### Módulos
Um pacote por módulo em `br.com.condomix`, cada um com um `package-info.java` descrevendo sua responsabilidade:
`plataforma`, `acesso`, `documentos`, `financeiro`, `reservas` e `comunicacao`.

### Testes
- `CondomixApplicationTests`: sobe a aplicação inteira contra um PostgreSQL de teste (Testcontainers).
- `ModularityTests`: verifica que nenhum módulo acessa as partes internas de outro e gera
  a documentação dos módulos em `target/spring-modulith-docs`.

## Como verificar

```bash
./mvnw test                 # precisa do Docker rodando
./mvnw spring-boot:run      # depois abra http://localhost:8080/actuator/health
```

Resultado esperado: 3 testes passando e `{"status":"UP"}` no health.

## Pendências registradas
- Instalar o JDK 25. A máquina tem só o Java 27, e o ArchUnit (usado pelo `ModularityTests`) ainda
  não lê as classes do Java 27 (avisos `Unsupported class file major version 71`). Os testes passam,
  mas a análise das dependências entre módulos fica incompleta.
- Instalar o Node (pelo nvm) para a etapa de frontend.
