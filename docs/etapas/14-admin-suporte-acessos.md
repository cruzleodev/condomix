# Etapa 14 — Administrador: síndico, bloqueio de login, fim do suporte e histórico

- **Branch:** `feature/admin-suporte-acessos` (criada a partir de `desenvolvimento`)
- **Concluída em:** 19/11/2026

## Objetivo

Esta é uma etapa curta, antes dos boletos. Ela fecha as lacunas do painel da plataforma no dia a dia do suporte:
1. **Novo convite para o síndico** que perdeu o link, e **troca de síndico** quando ele muda.
2. **Bloquear um login**, por exemplo numa suspeita de invasão da conta. O bloqueio vale na hora, inclusive para quem já está com o app aberto.
3. **Encerrar o acesso de suporte no servidor** antes de 1 hora.
4. **Histórico da assinatura** de cada cliente: cada mudança, com a data e quem fez.

## Telas (painel da plataforma)

| Tela | O que tem |
|---|---|
| **Clientes** | O nome do condomínio agora abre a **página do cliente**. Assinatura, suspensão e reativação saíram da lista e foram para lá |
| **Página do cliente** (nova) | **Assinatura**: plano, periodicidade e situação, com "Alterar". **Acesso ao app**: suspender ou reativar, e "Entrar como suporte". **Síndico**: nome, e-mail, mandato e situação do login (já criou, convite vale até... ou convite vencido); "Gerar novo convite" (permite corrigir o e-mail) e "Trocar síndico". **Histórico da assinatura**: quando, o que mudou, como ficou e quem fez |
| **Logins** (nova) | Busca por parte do e-mail (pelo menos 3 letras). Mostra os condomínios e papéis de cada login, o último acesso e a situação, com "Bloquear" (motivo obrigatório) e "Desbloquear". Ninguém consegue bloquear o próprio login |
| **Suporte** | Na auditoria, a coluna **Situação**: "Em andamento até..." (com o botão **Encerrar**), "Encerrado em..." ou "Venceu em..." |
| **Modo suporte** | "Voltar ao painel" agora também encerra o acesso no servidor |

## Como funciona

### Validação do token em cada requisição
O token JWT continua valendo até o prazo. Agora a API também confere, a cada requisição (`ValidadorDeSessao`), duas coisas:
- se o login não foi bloqueado depois que o token foi emitido;
- no token de suporte, se o acesso não foi encerrado.

Se uma das duas falhar, a resposta é 401: o app volta ao login ou, no caso do suporte, ao painel. São duas consultas
pela chave primária, em tabelas da plataforma (sem `tenant_id`).

### Bloqueio
- O bloqueio vale para **o login inteiro**: um síndico profissional bloqueado perde o acesso a todos os condomínios dele.
  A tela avisa quantos condomínios o login tem.
- No login, o bloqueado vê "Seu acesso está bloqueado. Fale com o suporte do Condomix.". A mensagem só aparece para
  quem acertou a senha; com a senha errada, continua "E-mail ou senha inválidos".

### Síndico
- **Novo convite:** só para o síndico que ainda não criou o login. O convite anterior deixa de valer.
- **Troca de síndico:** registra o mandato do novo síndico (o anterior termina na véspera da posse) e gera o convite.
  Se o novo síndico já for uma pessoa do condomínio (mesmo CPF/CNPJ), o cadastro dele é aproveitado, sem duplicar. Se ele
  já tem login no Condomix, não há convite: o acesso de síndico vem do mandato.
- O mandato continua limitado a 2 anos (Código Civil, art. 1.347).

### Histórico da assinatura
Nova tabela `evento_assinatura`, com os tipos: entrou como cliente, trocou o plano, mudou a situação, acesso suspenso e
acesso reativado. Salvar sem mudar nada não entra no histórico. A migração V8 preencheu o histórico dos clientes que já
existiam a partir das assinaturas gravadas. Nesses casos, quem fez fica como "Sistema".

## Decisões
- **Ver o síndico do cliente** (nome, e-mail, mandato) é um dado comercial, do contato do cliente, e não um dado interno do condomínio.
  Pelo mesmo motivo, a tela de Logins mostra só o e-mail, os papéis e os condomínios de cada login, sem dados pessoais.
- A troca de síndico pelo painel é para quando ninguém do condomínio consegue entrar. No dia a dia, quem registra a
  eleição é o síndico, pela tela Cargos e mandatos.

## Como verificar

```bash
./mvnw test                  # backend: 85 testes (5 novos em SuporteEAcessosTests)
cd frontend && npm test      # frontend: 23 testes
```

### Verificado no navegador (banco temporário, computador e celular)
1. **Página do cliente:** troquei o Residencial Exemplo para anual e ativa, suspendi e reativei. O histórico mostrou as três
   mudanças com `admin@condomix.dev` e a entrada inicial como "Sistema".
2. **Síndico:** registrei uma nova síndica, e o convite apareceu. O cartão mostrou "Convite vale até 26/11/2026"; gerei um
   novo link, que substituiu o anterior.
3. **Logins:** bloqueei o `morador@condomix.dev`. O token que ele já tinha passou a receber 401, e o login respondeu
   "Seu acesso está bloqueado...". Depois de desbloquear, ele entrou de novo.
4. **Suporte:** entrei no Residencial Exemplo e cliquei em "Voltar ao painel". O token de suporte passou a receber 401
   e a auditoria mostrou "Encerrado em". No celular, o botão "Encerrar" da auditoria também funcionou.
5. **Correção:** no menu da plataforma, "Clientes" aparecia marcado em todas as telas. Agora ele só fica marcado na lista e
   na página de cada cliente.

## Pendências
- **Esqueci minha senha** (para síndicos e moradores que já criaram o login): depende do envio de e-mail (etapa de notificações).
- **Boletos e Pix pelo gateway:** etapa 15.
