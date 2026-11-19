# Etapa 13 — Frontend parte 4: painel do administrador da plataforma

- **Branch:** `feature/frontend-plataforma` (criada a partir de `desenvolvimento`)
- **Concluída em:** 19/11/2026

## Objetivo

O dono do Condomix administra os clientes **pelo app**, sem Swagger: cadastra condomínios, define planos e
preços, troca e suspende assinaturas e atende clientes pelo **modo suporte**, que é somente leitura e auditado.
A API já existia (etapa 6); esta etapa entrega as telas.

## Painel da plataforma

Quem entra com o papel `ADMIN_PLATAFORMA` (ex.: `admin@condomix.dev`) cai direto no painel. Ele tem um layout
próprio, separado do app do condomínio:
- **computador:** menu lateral escuro;
- **celular:** abas no topo.

| Tela | O que tem |
|---|---|
| **Clientes** (`/plataforma`) | Lista de condomínios com tipo, CNPJ, plano e periodicidade, situação da assinatura, data de entrada e acesso (liberado ou suspenso). Ações: **Assinatura** (troca plano, periodicidade e situação: em teste, ativa ou suspensa), **Suspender**/**Reativar** (com confirmação) e **Suporte**. **Novo cliente**: dados do condomínio, plano e síndico. O mandato do síndico vem preenchido com 1 ano. O resultado mostra o **link do convite do síndico** com "Copiar link" |
| **Planos e preços** (`/plataforma/planos`) | Cada plano com os módulos e os preços mensal, semestral e anual. **Alterar preços** aceita `149,90` ou `1.499,00` |
| **Suporte** (`/plataforma/suporte`) | **Entrar num condomínio**: escolha do condomínio e motivo (mínimo de 10 letras). **Acessos registrados**: quem abriu, quando, até quando e o motivo |

## Modo suporte

1. O administrador abre o acesso na tela Suporte e o app troca para o token de suporte, que vence em 1 hora.
2. Ele vê o condomínio **como a gestão vê**: início, unidades, moradores, mandatos, taxa, cobranças e caixa.
   Não aparece nenhum botão de ação. A API recusa qualquer escrita (só GET), e essa é a barreira real.
3. Uma **faixa amarela fixa** mostra "Modo suporte · somente leitura · até 11:16 · acesso registrado".
   O botão **"Voltar ao painel"** devolve a sessão do administrador.
4. Se o token de suporte vencer durante o uso, o app **volta sozinho para a sessão do administrador** em vez de deslogar.
   A sessão do administrador fica guardada em `anterior`.
5. Ao entrar e ao sair do suporte, o cache das telas é limpo, para os dados de um condomínio não aparecerem em outro.

### Também no backend
`Permissoes.CONSULTA_DO_CADASTRO` passou a incluir `SUPORTE`. Antes, o suporte via o início, mas recebia 403 nas
telas de gestão (unidades, moradores, cobranças, caixa). O teste do modo suporte agora confere:
- GET em `/api/unidades`, `/api/pessoas`, `/api/cobrancas` e `/api/financeiro/contas` responde 200;
- POST em `/api/unidades` responde 403.

## Para onde vai quem não escolheu condomínio

| Situação | Destino |
|---|---|
| Administrador da plataforma | Painel (`/plataforma`) |
| Tem condomínios, mas ainda não escolheu | Escolha do condomínio |
| Nenhum condomínio ativo (convite não aceito, condomínio suspenso) | Nova tela **"Sem acesso a condomínios"** |

O administrador que também participa de algum condomínio tem o link **"Entrar num condomínio"** no menu do painel,
e **"Painel da plataforma"** na tela Conta.

## Ajuste encontrado ao usar
Plano sem preço vem da API como `null`, e o botão "Alterar preços" quebrava. Corrigido: o campo abre vazio.

## Como verificar

```bash
./mvnw test                  # backend: 80 testes
cd frontend && npm test      # frontend: 21 testes (4 novos do modo suporte e do destino sem condomínio)
```

### Verificado no navegador (banco temporário, computador e celular)
1. **Login como admin** caiu no painel com os 2 condomínios de exemplo.
2. **Novo cliente:** "Residencial Jardim Teste" no plano Essencial anual, com síndica Carla. O link do convite apareceu e
   o cliente entrou na lista "Em teste".
3. **Assinatura:** troquei para Completo, ativa. **Suspender** marcou o acesso como suspenso e **Reativar** voltou
   para "3 ativos".
4. **Preços:** Essencial com R$ 149,90, R$ 809,46 e R$ 1.499,00.
5. **Suporte:** entrei no Residencial Exemplo com motivo. Apareceu a faixa amarela e as 6 telas de gestão abriram (todas as
   chamadas com 200, sem botões de ação). No celular, "Voltar ao painel" devolveu o admin, e a auditoria
   mostrou o acesso com início, vencimento e motivo.

### Para você testar
Com a API rodando, rode `cd frontend && npm run dev` e abra http://localhost:5173. Entre com o e-mail `admin@condomix.dev` e a senha `condomix123`.

## Pendências
- **Cobrança da assinatura** do Condomix pelo gateway e situação financeira de cada cliente: etapa 15.
- **Histórico de assinaturas** de um cliente na tela (a API guarda; falta a visualização).
- **Encerrar um acesso de suporte antes do prazo** no servidor (hoje o app descarta o token, que vence em 1 hora).
