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
| 5 | [Cargos e mandatos: síndico, subsíndico e conselho](etapas/05-cargos-mandatos.md) | `feature/cargos-mandatos` | Concluída |
| 6 | [Administração da plataforma: condomínios, planos e assinaturas](etapas/06-administracao-plataforma.md) | `feature/administracao-plataforma` | Concluída |
| 7 | [Cadastro de unidades, pessoas e moradores pelo síndico](etapas/07-cadastro-unidades-pessoas.md) | `feature/cadastro-unidades-pessoas` | Concluída |
| 8 | [Financeiro: cobranças, rateio, inadimplência e importação da administradora](etapas/08-financeiro-cobrancas.md) | `feature/financeiro-cobrancas` | Concluída |
| 9 | [Financeiro: caixa, despesas e balancete](etapas/09-financeiro-caixa.md) | `feature/financeiro-caixa` | Concluída |
| 10 | [Frontend parte 1: base do app (PWA), login, telas do morador e documentos](etapas/10-frontend-base.md) | `feature/frontend-base` | Concluída |
| 11 | [Frontend parte 2: layout para computador (todos os usuários) e gestão do condomínio](etapas/11-frontend-gestao.md) | `feature/frontend-gestao` | Concluída |
| 12 | [Frontend parte 3: financeiro do síndico (taxa, geração, cobranças, inadimplência, caixa)](etapas/12-frontend-financeiro.md) | `feature/frontend-financeiro` | Concluída |
| 13 | [Frontend parte 4: painel do administrador da plataforma (clientes, planos, assinaturas e modo suporte)](etapas/13-frontend-plataforma.md) | `feature/frontend-plataforma` | Concluída |
| 14 | [Administrador: convite e troca de síndico, bloqueio de login, fim do suporte e histórico da assinatura](etapas/14-admin-suporte-acessos.md) | `feature/admin-suporte-acessos` | Concluída |
| 15 | [Boletos e Pix pelo gateway (Asaas), 2ª via e baixa automática](etapas/15-boletos-pix.md) | `feature/boletos-pix` | Concluída |
| 16 | [Cobrança da assinatura do Condomix: teste grátis, faturas com boleto e Pix, suspensão por atraso](etapas/16-assinatura-pelo-gateway.md) | `feature/assinatura-gateway` | Concluída |
| 17 | [Reservas: espaços, agenda, regras e aluguel](etapas/17-reservas.md) | `feature/reservas` | Concluída |
| 18 | [Notificações: no app, por e-mail e push](etapas/18-notificacoes.md) | `feature/notificacoes` | Concluída |
| 19 | App nas lojas (Capacitor) | — | Próxima |
| 20 | Bot de WhatsApp | — | Pendente |

A partir da etapa 14, cada etapa é uma **fatia vertical**: entrega o backend **e** as telas juntos.

Futuro, sem data: assembleias online (convocação, pauta, votação e ata).

## Decisões de negócio

| Tema | Decisão |
|---|---|
| Síndico morador × profissional | Ser síndico é um cargo (perfil), não depende de morar. O síndico morador tem os perfis MORADOR e SINDICO; o profissional, só SINDICO, e pode atender vários condomínios com um único login |
| Mandato e passagem | Cargos com mandato (posse, fim previsto, ata da eleição). Ao registrar a eleição, o mandato anterior se encerra na data de posse do sucessor; o histórico é mantido. Avisos 90, 60 e 30 dias antes do fim |
| Mandato vencido sem eleição | **Prorrogar com aviso** até a posse do sucessor (padrão). Configurável por condomínio para "suspender", conforme a convenção |
| Administrador da plataforma | Papel `ADMIN_PLATAFORMA`, fora dos condomínios: gerencia condomínios, planos, assinaturas e usuários pela aplicação, nunca direto no banco. Não vê dados internos dos condomínios, exceto em "modo suporte" temporário e auditado (LGPD). Pode bloquear um login (em todos os condomínios) e cuidar do síndico do cliente (novo convite, troca) |
| Desconto do síndico | Desconto ou isenção definidos no mandato, aplicados à unidade indicada só enquanto o mandato vigorar (proporcional aos dias). Síndico profissional recebe remuneração, que é despesa, não desconto. Implementado como uma `RegraDeDesconto` |
| Reservas | A reserva vale na hora, se passar nas regras do espaço (antecedência, limite por mês, bloqueio de inadimplente) e o horário estiver livre. Com aluguel, vira cobrança avulsa da unidade. Cancelar no prazo do espaço cancela o aluguel; depois, continua devido (a gestão pode dispensar). Por ora, liberadas em todos os planos |
| "Meus dados" | Telas como "minhas cobranças" e "minhas reservas" usam os vínculos da pessoa com unidades, não o perfil MORADOR |

## Decisões de arquitetura

| Área | Decisão |
|---|---|
| Backend | Java 25, Spring Boot 4.1 e Spring Modulith (um sistema só, dividido em módulos) |
| Frontend | React + TypeScript (PWA); depois app nas lojas com Capacitor |
| Banco | PostgreSQL 18 com Flyway; todos os condomínios no mesmo banco, separados por `tenant_id` |
| Mensageria | Por ora, os eventos entre módulos ficam guardados no próprio Postgres (Spring Modulith) e são reprocessados se falharem. O RabbitMQ entra quando houver mais de um servidor ou volume alto (troca de configuração) |
| WhatsApp | API oficial da Meta (Cloud API) |
| Pagamentos | Asaas: cada condomínio tem a própria conta, e o administrador cola a chave de API dela no painel (guardada cifrada). O dinheiro do morador cai direto na conta do condomínio. Boleto com Pix emitido na geração da taxa; baixa automática pelo aviso (webhook), conferida na API do Asaas |
| Assinatura do Condomix | Cobrada por boleto e Pix na conta Asaas do próprio Condomix (chave no painel, cifrada). Cliente novo tem 30 dias de teste grátis; cada fatura sai 10 dias antes do período e vence no início dele; com 15 dias de atraso o acesso é suspenso (a gestão ainda entra, só para pagar) e volta sozinho ao pagar. Troca de plano vale na próxima fatura |
| Planos | Essencial (Documentos + Financeiro), Completo (+ Reservas) e WhatsApp como adicional |
| Git | `desenvolvimento` → `homologacao` → `producao`, sempre por pull request; uma branch `feature/*` e um commit por etapa |

## Módulos

| Módulo | Responsabilidade |
|---|---|
| `compartilhado` | Peças comuns a todos os módulos (condomínio da requisição atual, erros, paginação, relógio) |
| `administracao` | Painel do dono do Condomix: clientes, planos, assinaturas e suporte (`/api/plataforma/**`) |
| `plataforma` | Condomínios (tenants), planos e assinaturas do Condomix |
| `acesso` | Unidades, pessoas, usuários e perfis de acesso |
| `documentos` | Convenção, regimento, leis e atas |
| `financeiro` | Cobranças, boletos/Pix, histórico e balancete |
| `pagamentos` | A ligação com os gateways (Asaas e o simulado), usada pelo financeiro e pelo faturamento |
| `faturamento` | A assinatura que cada condomínio paga ao Condomix: faturas, baixa e suspensão por atraso |
| `reservas` | Espaços comuns, agenda e aluguel |
| `comunicacao` | Notificações no app (o sino), por e-mail e push; depois WhatsApp e bot |

As regras de design (SOLID) e as convenções do código estão no [CLAUDE.md](../CLAUDE.md).

Os módulos só se comunicam pelas classes públicas do pacote raiz de cada um.
O teste `ModularityTests` falha se essa regra for quebrada.
