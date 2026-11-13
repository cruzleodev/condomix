# Documentação do Condomix

Sistema web de gestão de condomínios (residenciais e comerciais), vendido como SaaS por assinatura.
Usado principalmente pelo celular (PWA) e, depois, como app nas lojas.

## Etapas

Cada etapa é feita numa branch `feature/*` e documentada aqui ao terminar.

| # | Etapa | Branch | Situação |
|---|---|---|---|
| 1 | [Fundação](etapas/01-fundacao.md) | `feature/fundacao` | Concluída |
| 2 | [Cadastro base](etapas/02-cadastro-base.md) | `feature/cadastro-base` | Concluída |
| 3 | [Login com JWT e separação por condomínio](etapas/03-autenticacao.md) | `feature/autenticacao` | Concluída |
| 4 | [Documentos: upload, categorias e busca](etapas/04-documentos.md) | `feature/documentos` | Concluída |
| 5 | Financeiro (consulta): histórico de pagamentos e balancete | — | Próxima |
| 6 | Boletos e Pix pelo gateway, 2ª via e baixa automática | — | Pendente |
| 7 | Reservas: espaços, agenda, regras e pagamento por Pix | — | Pendente |
| 8 | Mensageria e notificações: RabbitMQ, e-mail e push | — | Pendente |
| 9 | Bot de WhatsApp | — | Pendente |
| 10 | Frontend: PWA e depois app nas lojas | — | Pendente |
| 11 | Cobrança da assinatura do Condomix | — | Pendente |

## Decisões de arquitetura

| Área | Decisão |
|---|---|
| Backend | Java 25, Spring Boot 4.1 e Spring Modulith (um sistema só, dividido em módulos) |
| Frontend | React + TypeScript (PWA); depois app nas lojas com Capacitor |
| Banco | PostgreSQL 18 com Flyway; todos os condomínios no mesmo banco, separados por `tenant_id` |
| Mensageria | RabbitMQ |
| WhatsApp | API oficial da Meta (Cloud API) |
| Pagamentos | Gateway com subcontas (ex.: Asaas): o dinheiro do morador cai na conta do condomínio |
| Planos | Essencial (Documentos + Financeiro), Completo (+ Reservas) e WhatsApp como adicional |
| Git | `desenvolvimento` → `homologacao` → `producao`, sempre por pull request; uma branch `feature/*` e um commit por etapa |

## Módulos

| Módulo | Responsabilidade |
|---|---|
| `compartilhado` | Peças comuns a todos os módulos (condomínio da requisição atual) |
| `plataforma` | Condomínios (tenants), planos e assinaturas do Condomix |
| `acesso` | Unidades, pessoas, usuários e perfis de acesso |
| `documentos` | Convenção, regimento, leis e atas |
| `financeiro` | Cobranças, boletos/Pix, histórico e balancete |
| `reservas` | Espaços comuns, agenda e aluguel |
| `comunicacao` | WhatsApp, e-mail, push e bot |

As regras de design (SOLID) e as convenções do código estão no [CLAUDE.md](../CLAUDE.md).

Os módulos só se comunicam pelas classes públicas do pacote raiz de cada um.
O teste `ModularityTests` falha se essa regra for quebrada.
