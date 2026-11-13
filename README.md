# condomix
Sistema de gestão de condomínios (SaaS)

## Requisitos
- Java 25
- Docker Desktop (rodando)

## Rodando localmente
Rode a classe `CondomixApplication` no IntelliJ (ou `./mvnw spring-boot:run`).
O Spring Boot sobe o PostgreSQL definido em `compose.yaml` automaticamente.

- API: http://localhost:8080
- Saúde: http://localhost:8080/actuator/health
- Swagger: http://localhost:8080/swagger-ui/index.html

### Logins de exemplo (perfil dev)
Criados na primeira execução. Senha: `condomix123`.
- `sindico@condomix.dev`: síndico de 2 condomínios
- `morador@condomix.dev`: morador de 1 condomínio

## Banco (DBeaver)
| Campo | Valor |
|---|---|
| Host | localhost |
| Porta | 5432 |
| Banco | condomix |
| Usuário / senha | condomix / condomix |

## Testes
`./mvnw test` — usa Testcontainers (precisa do Docker rodando).
`ModularityTests` verifica que os módulos não acessam as partes internas uns dos outros.

## Documentação
Decisões de arquitetura e o registro de cada etapa estão em [docs/](docs/README.md).
