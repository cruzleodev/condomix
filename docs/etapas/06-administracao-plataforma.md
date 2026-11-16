# Etapa 6 — Administração da plataforma

- **Branch:** `feature/administracao-plataforma` (criada a partir de `desenvolvimento`)
- **Concluída em:** 16/11/2026

## Objetivo

Dar ao dono do Condomix um papel próprio para gerenciar o negócio pela aplicação, e não direto no
banco: cadastrar clientes, definir preços, controlar assinaturas, suspender quem não pagou e dar
suporte com acesso auditado.

## O que foi feito

### Administrador da plataforma (`ADMIN_PLATAFORMA`)
- É um papel **fora dos condomínios**, marcado no próprio login (`usuario.admin_plataforma`).
- Todas as rotas `/api/plataforma/**` exigem esse papel.
- **Não vê dados internos dos condomínios** (documentos, pessoas, mandatos). Para isso existe o modo suporte.
- **Primeiro administrador:** é criado ao subir a aplicação, se ainda não houver nenhum
  (`AdministradorInicial`). Esse é o único passo feito fora do sistema.

| Ambiente | Como definir |
|---|---|
| Desenvolvimento | Já configurado: `admin@condomix.dev` / `condomix123` |
| Produção | Variáveis `CONDOMIX_ADMIN_EMAIL` e `CONDOMIX_ADMIN_SENHA` (mínimo 10 caracteres) |

### Origem de cada papel
O enum `Perfil` passou a dizer de onde vem cada papel:

| Origem | Papéis | Onde fica |
|---|---|---|
| `MANDATO` | SINDICO, SUBSINDICO, CONSELHEIRO | tabela `mandato` |
| `PERFIL` | MORADOR, ADMINISTRADORA, PORTARIA | tabela `perfil_acesso` |
| `PLATAFORMA` | ADMIN_PLATAFORMA, SUPORTE | no login, ou no token de suporte |

### Cliente novo (entrada de um condomínio)
`POST /api/plataforma/condominios` faz tudo de uma vez:
1. cria o condomínio, com a assinatura em **teste**;
2. registra o **primeiro síndico** (pessoa e mandato);
3. gera o **convite** para o síndico criar o login. O link vem na resposta.

Os dados do mandato são conferidos **antes** de criar qualquer coisa, para não sobrar um condomínio
sem síndico.

### Convites de acesso
Servem tanto para o primeiro síndico quanto para o síndico convidar moradores, a administradora ou a portaria.

| Método | Rota | Quem | O que faz |
|---|---|---|---|
| POST | `/api/convites` | síndico, administradora | Convida uma pessoa do condomínio (perfil opcional) |
| POST | `/api/convites/consulta` | aberto | Mostra do que se trata o convite (condomínio, pessoa, e-mail) |
| POST | `/api/convites/aceite` | aberto | Cria o login com a senha, ou liga o login que a pessoa já tem |

Regras:
- Vale **7 dias** e pode ser usado **uma vez**. Um convite novo para a mesma pessoa cancela o anterior.
- O código é **aleatório** (32 bytes), e no banco fica só a **impressão digital** dele (SHA-256), como numa senha.
- O código vai sempre **no corpo** da requisição, nunca no endereço, porque endereços ficam gravados em logs.
- **Quem já tem login** em outro condomínio confirma com a senha atual (prova de que é a mesma pessoa)
  e passa a ver os dois condomínios no mesmo login.
- A mesma resposta é dada para código inexistente, usado ou expirado. Assim, ninguém descobre códigos válidos por tentativa.
- Enquanto não houver envio de e-mail (etapa de notificações), **quem convida repassa o link**.

### Planos e preços
| Método | Rota | O que faz |
|---|---|---|
| GET | `/api/plataforma/planos` | Planos, módulos e preços |
| PUT | `/api/plataforma/planos/{codigo}/precos` | Define os preços mensal, semestral e anual |

Os preços começam **vazios**: você define os valores.

### Condomínios e assinaturas
| Método | Rota | O que faz |
|---|---|---|
| GET | `/api/plataforma/condominios` | Todos os clientes, com plano e situação da assinatura |
| GET | `/api/plataforma/condominios/{id}` | Um cliente |
| POST | `/api/plataforma/condominios/{id}/suspensao` | Suspende: **ninguém do condomínio entra mais** |
| POST | `/api/plataforma/condominios/{id}/reativacao` | Reativa |
| PUT | `/api/plataforma/condominios/{id}/assinatura` | Troca o plano, a periodicidade ou a situação (teste, ativa, suspensa) |

Trocar o plano ou a periodicidade **encerra a assinatura atual**, que fica no histórico, e abre uma nova.

### Modo suporte (LGPD)
| Método | Rota | O que faz |
|---|---|---|
| POST | `/api/plataforma/suporte` | Abre o acesso a um condomínio, com **motivo obrigatório** (mínimo 10 caracteres) |
| GET | `/api/plataforma/suporte` | Auditoria: quem acessou, qual condomínio, quando e por quê |

O token de suporte:
- vale **1 hora**;
- tem o papel `SUPORTE`;
- é **só de leitura**: qualquer requisição que não seja GET é recusada com 403.

## Decisões

- **Novo módulo `administracao`:** fica **acima** de `plataforma` e de `acesso` e usa só as APIs públicas dos dois.
  Sem ele, `plataforma` dependeria de `acesso` para criar o síndico, e `acesso` dependeria de `plataforma` para
  consultar condomínios: uma dependência circular.
- **APIs públicas novas:**
  - `plataforma`: `PainelDeCondominios` e `CatalogoDePlanos`;
  - `acesso`: `IngressoDeSindico` e `SuporteAoCondominio`.
- **`comoSistema` no painel:** a lista de clientes lê as assinaturas de todos os condomínios. A regra
  do CLAUDE.md foi ajustada: isso é permitido para dados **da plataforma** (condomínio, plano,
  assinatura), nunca para dados internos dos condomínios.
- **Suspender, não apagar:** encerrar um cliente é suspender o condomínio. Assinatura cancelada só
  acontece na troca de plano.

## Como verificar

```bash
./mvnw test
```

Resultado esperado: 48 testes passando. Os 7 do `AdministracaoTests` cobrem:
- só o administrador entra na plataforma;
- cliente novo, com o convite aceito e o login da síndica;
- mandato inválido não cria nada;
- morador de um condomínio convidado para outro, confirmando com a própria senha;
- preços;
- troca de plano e suspensão, que tira e devolve o acesso;
- modo suporte só de leitura e registrado.

Verificado também na aplicação real:
- **no seu banco:** a migração V4 e a criação de `admin@condomix.dev`;
- **num banco temporário:** cliente novo → convite aceito → síndica entra → suporte lê, mas não grava.

### No Swagger

1. Entre com `admin@condomix.dev` / `condomix123` e autorize com o token. Não é preciso escolher condomínio.
2. **Plataforma: condomínios → `GET /api/plataforma/condominios`** mostra os clientes.
3. **`POST /api/plataforma/condominios`** cadastra um cliente novo. Na resposta, em `conviteDoSindico.link`, copie o que vem depois de `codigo=`.
4. **Convites → `POST /api/convites/aceite`**: use `{"codigo": "...", "senha": "uma-senha-qualquer"}`.
5. Entre com o e-mail do síndico e a senha escolhida: ele vê o condomínio novo, com o perfil `SINDICO`.

## Pendências para etapas futuras

- **Segundo fator de autenticação** (código no celular) para o administrador.
- **Envio do convite por e-mail** (etapa de notificações). Hoje o link é repassado à mão.
- **Plano adicional WhatsApp** por condomínio (contratar e cancelar o adicional).
- **Cobrança das assinaturas** pelo gateway (etapa final do roteiro).
- **Criar outros administradores** e o "esqueci minha senha".
- **Registrar cada requisição** feita no modo suporte. Hoje é registrada a abertura do acesso.
