# Etapa 16 — Cobrança da assinatura do Condomix pelo gateway

- **Branch:** `feature/assinatura-gateway` (criada a partir de `desenvolvimento`)
- **Concluída em:** 19/11/2026

## Objetivo

O Condomix cobra sozinho a assinatura de cada condomínio, por boleto e Pix, na conta Asaas do próprio Condomix.
O síndico paga pelo app, e quem atrasa demais tem o acesso suspenso até pagar.
Na etapa 15 o dinheiro ia **do morador para o condomínio**; agora vai **do condomínio para o Condomix**.

## Decisões (tomadas com você no início da etapa)

| Tema | Decisão |
|---|---|
| Conta que recebe | A chave de API da **conta Asaas do Condomix** é colada no painel (Faturas → Conta do Condomix no Asaas) e guardada **cifrada**, como a dos condomínios. Dá para trocar sem reiniciar o servidor |
| Início da cobrança | **Teste grátis de 30 dias** para cliente novo. Ao fim, a assinatura fica **ativa sozinha** e sai a primeira fatura. O prazo de cada cliente pode ser mudado (ou ficar sem prazo) |
| Atraso | O síndico vê um aviso desde o vencimento. Com **15 dias de atraso** o acesso é **suspenso sozinho**, e volta sozinho quando a fatura é paga |
| Troca de plano | **Vale na próxima fatura**: o período já faturado fica como está, sem proporcional |

## Como funciona

### O calendário (`CalendarioDeFaturas`)
- Cada fatura cobra um período do plano (mês, semestre ou ano) e é **paga antes** de o período começar.
- Ela é gerada **10 dias antes** do período e vence no primeiro dia dele.
- A primeira fatura, ou a primeira depois de uma suspensão, começa hoje e vence em **7 dias**. O tempo sem acesso não é cobrado.
- **Suspensão:** se a fatura continuar sem pagamento, o acesso é suspenso no 16º dia depois do vencimento.

### A rotina (`RotinaDeFaturamento`), de hora em hora
1. Os testes grátis que chegaram ao fim viram assinaturas **ativas** (histórico: "Fim do teste: passou a pagar").
2. Cada assinatura ativa de um condomínio não suspenso ganha a fatura do próximo período, quando chega a hora. O valor é o preço do plano na periodicidade.
   - Plano **sem preço** não gera fatura (fica no log).
   - **Sem a conta do Condomix ligada**, nenhuma fatura é gerada e ninguém é suspenso.
3. Quem tem fatura emitida há mais de 15 dias do vencimento é **suspenso por falta de pagamento**.

Repetir a rotina no mesmo dia não gera nada de novo. O banco também impede faturar o mesmo período duas vezes. O
painel tem o botão **"Rodar a cobrança agora"** para não esperar a próxima hora.

### Emissão e baixa (iguais às da etapa 15)
- A `EmissaoDeFaturas` emite o boleto (com Pix) **fora da transação do banco**, a cada 10 segundos.
  - O pagador é o **condomínio**, pelo **CNPJ**.
  - Sem CNPJ, a fatura fica "Com problema", com o motivo; o administrador cadastra o CNPJ e clica em "Tentar de novo".
- O boleto sai com multa de 2% e juros de 1% ao mês (`condomix.faturamento.multa-percentual` e `juros-mensal-percentual`).
- O Asaas avisa em `POST /api/avisos/asaas/assinaturas`, com o token da conta do Condomix no cabeçalho `asaas-access-token`.
  - O Condomix **confere o pagamento na API do Asaas** antes de dar baixa.
  - Aviso repetido não faz nada.
  - Pago, se não sobrar outra fatura além da tolerância, o acesso **volta sozinho**.

### A suspensão
- O condomínio agora guarda **por que** está suspenso: `MANUAL` (o administrador) ou `FALTA_DE_PAGAMENTO` (a rotina).
- Só a suspensão por falta de pagamento volta sozinha. Se o administrador suspender à mão um condomínio já suspenso
  por atraso, o motivo vira manual e só ele reativa.
- **Com o acesso suspenso por falta de pagamento**, os moradores não entram. O **síndico e a administradora entram, mas só
  para pagar**:
  - o token sai com a marca `pagamento_pendente`, e a API só deixa passar `/api/assinatura/**`;
  - o app abre direto na tela da assinatura, e o menu fica só com "Assinatura" e "Conta".
- Assim que o pagamento é confirmado, a tela troca o acesso restrito pelo normal e volta ao início.

### Perdão
O administrador pode **cancelar** uma fatura, e o boleto é cancelado no gateway. O período **não é cobrado de novo**.
Se era essa a fatura que mantinha o acesso suspenso, o acesso volta.

## Telas

| Tela | O que mudou |
|---|---|
| **Faturas** (plataforma, novo menu) | **Conta do Condomix no Asaas**: ligar com a chave, conferida na hora, e o endereço e o token do webhook mostrados uma vez. Também "Como a cobrança funciona", com o botão "Rodar a cobrança agora". As faturas de todos os clientes têm filtros (em atraso, em aberto, com problema, pagas, todas) e as ações 2ª via, Tentar de novo, Cancelar e, só no simulado, Simular pagamento |
| **Página do cliente** (plataforma) | Na seção Assinatura, **"Teste grátis até"**, com o botão "mudar". Seção nova **Dados para a fatura** (CNPJ, e-mail e telefone; sem CNPJ, o aviso em vermelho). Seção **Faturas da assinatura**. O acesso mostra o motivo da suspensão. O histórico tem os novos acontecimentos e mostra o prazo do teste |
| **Clientes** (plataforma) | "Suspenso (pagamento)" para quem foi suspenso pela rotina |
| **Assinatura do Condomix** (gestão: síndico e administradora) | O plano, o valor e o teste grátis. As faturas em aberto e pagas, com **Pix e boleto** ("Pagar com Pix ou boleto"). Também tem o atalho na tela Gestão |
| **Aviso no alto do app** (gestão) | "A fatura do Condomix venceu em… Se não for paga, o acesso será suspenso em…", com o link **Pagar agora** |
| **Escolher o condomínio** | O suspenso por falta de pagamento aparece com "Acesso suspenso: entre para pagar a fatura do Condomix" |

## Arquitetura (SOLID)
- **Módulo novo `pagamentos`** (O, L, D): a interface `GatewayDePagamento`, as implementações Asaas e simulada e o teste de
  contrato saíram do `financeiro`. Agora o financeiro e o faturamento usam a mesma ligação, cada um com a credencial da própria
  conta, sem um depender do outro. O botão de simulação usa a interface `SimuladorDePagamentos`.
- **Módulo novo `faturamento`**: `Fatura` (com `tenant_id`, para o síndico ver só as suas), `ContaDeRecebimento` (a única conta do
  Condomix, sem tenant), a rotina, a emissão, a baixa e a tela do síndico (`/api/assinatura`).
- **`AssinaturasParaFaturamento`** (I): a porta da plataforma só com o que o faturamento precisa: testes vencidos, quem paga e
  quanto, quem é o pagador, suspender e reativar.
- **`Periodicidade`** sabe quantos meses cobre e qual preço do plano usar (comportamento no enum, sem `switch`).
- O painel (`administracao`) só fala com `FaturasDosClientes` e `ContaDoCondomixNoGateway`.

## Configuração
Nada de novo para produção, além do que a etapa 15 já pede: `CONDOMIX_COFRE_CHAVE` (cifra também a chave do Condomix) e
`CONDOMIX_ENDERECO_PUBLICO` (endereço do webhook). A rotina e a emissão rodam sozinhas; nos testes ficam desligadas
(`condomix.faturamento.automatico: false`).

## Como verificar

```bash
./mvnw test                  # backend: 118 testes (12 novos do faturamento)
cd frontend && npm test      # frontend: 30 testes
```

### Verificado no navegador (banco temporário, computador e celular)
1. **Painel → Faturas:** liguei a conta do Condomix no gateway simulado. O painel mostrou o endereço
   (`/api/avisos/simulado/assinaturas`) e o token.
2. **Residencial Exemplo:**
   - em Dados para a fatura, cadastrei o CNPJ;
   - mudei o teste grátis para terminar hoje;
   - cliquei em "Rodar a cobrança agora".
   Saiu a fatura de **R$ 199,90** (19/11/2026 a 18/12/2026, vencendo em 26/11/2026), e o boleto foi emitido em segundos.
3. **Síndico (celular):** em Gestão → Assinatura, apareceram o plano Completo · Mensal, a situação "Ativa" e a fatura
   "Vence em 26/11/2026". Atrasei a fatura no banco: o aviso vermelho apareceu no alto da tela Gestão.
4. **Suspensão:**
   - atrasei a fatura 17 dias e rodei a cobrança;
   - o morador entrou sem nenhum condomínio;
   - o síndico caiu direto na assinatura, com o aviso de suspensão, o menu só com "Assinatura" e "Conta" e o Pix da fatura aberto.
5. **Pagamento:** simulei o pagamento. Em poucos segundos, a tela do síndico voltou sozinha ao início, com o menu completo.
   - O histórico do cliente mostrou: Entrou como cliente → Mudou o prazo do teste → Fim do teste → Suspenso por falta de
     pagamento → Reativado: fatura paga.

## Pendências
- **Testar no sandbox do Asaas** com a conta de testes do Condomix, junto com a pendência da etapa 15.
- **Avisos por e-mail e push** (fatura disponível, vencendo, atrasada, acesso suspenso): etapa de notificações. Hoje o
  síndico só vê no app.
- **Nota fiscal** do Condomix (NFS-e) para cada fatura paga: o Asaas emite, mas ainda não está ligado.
- **Módulos adicionais** (ex.: WhatsApp): a fatura cobra só o plano base. Os adicionais entram quando forem contratáveis.
- **Mais de um servidor:** como na etapa 15, a rotina e a emissão supõem uma instância só.
- **Lembrete:** ao preparar homologação e produção, rever onde guardar a `CONDOMIX_COFRE_CHAVE` (uma por ambiente).
