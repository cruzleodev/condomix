# Etapa 15 — Boletos e Pix pelo gateway (Asaas), 2ª via e baixa automática

- **Branch:** `feature/boletos-pix` (criada a partir de `desenvolvimento`)
- **Concluída em:** 19/11/2026

## Objetivo

O morador paga a cobrança pelo app, com Pix ou boleto, e a cobrança é baixada sozinha. O síndico deixa de
conferir extrato e dar baixa à mão.

## Decisões (tomadas com você no início da etapa)

| Tema | Decisão |
|---|---|
| Conta no gateway | **Cada condomínio tem a própria conta Asaas.** O administrador da plataforma cola a chave de API dela no painel, e a chave fica guardada **cifrada** (AES-GCM, ver `Cofre`). O dinheiro cai direto na conta do condomínio |
| Sandbox | Ainda não há conta de testes no Asaas. Por isso existe também um **gateway simulado** (boleto e Pix de mentira, com o botão "Simular pagamento"), só em desenvolvimento e testes. O Asaas é testado com as respostas da API dele gravadas |
| Quando emitir | **Na geração da taxa do mês e na cobrança avulsa.** Multa e juros vão configurados no boleto, então ele continua pagável depois do vencimento |
| Tarifa do gateway | **Despesa automática** ("Tarifas bancárias") no caixa, ligada à mesma cobrança |

## Como funciona

### Emissão
1. Ao gerar a taxa ou lançar uma avulsa, com o gateway ligado, cada cobrança ganha um pedido de boleto (`PENDENTE`), na mesma transação.
   - As cobranças **importadas** da administradora ficam de fora, porque a administradora já emitiu o boleto delas.
2. O `ProcessadorDeBoletos` roda a cada 10 segundos e chama o gateway **fora da transação do banco**: a geração de
   centenas de cobranças não espera centenas de chamadas pela rede. Se o servidor cair no meio, os pedidos
   continuam gravados e são feitos na rodada seguinte.
3. **Cobrança já vencida:** o gateway não aceita vencimento no passado. O boleto sai com o valor atualizado até hoje
   (com multa e juros), vencendo hoje, e só os juros continuam correndo.
4. **Falhas** (ex.: o responsável não tem CPF, que o banco exige) aparecem para o síndico com o motivo e o botão
   "Tentar de novo". O CPF agora pode ser corrigido pela tela **Moradores**: clique no nome da pessoa.

### Baixa automática
1. O Asaas avisa (webhook) em `POST /api/avisos/asaas/{condominio}`, com o token do condomínio no cabeçalho
   `asaas-access-token`.
2. O Condomix **não confia no corpo do aviso**: consulta o pagamento na API do Asaas e só então dá baixa, com o
   valor pago (com multa e juros, se atrasou) e a forma (Pix ou boleto).
3. O caixa recebe o valor na conta **"Recebimentos online (boleto e Pix)"** (tipo gateway, criada sozinha), e a tarifa
   entra como despesa. Estornar o pagamento tira as duas coisas.
4. **Aviso repetido não dá baixa duas vezes.** Se o morador pagar um boleto de cobrança que já foi paga por fora ou cancelada,
   o pagamento fica registrado no log como **duplicidade**, para o síndico devolver.

### Cancelamentos
Cancelar a cobrança, ou receber por fora (dinheiro, transferência), cancela o boleto no gateway, para o morador
não pagar duas vezes. Estornando, "Emitir os boletos que faltam" gera um novo boleto.

## Telas

| Tela | O que mudou |
|---|---|
| **Meus débitos** (morador) | Na cobrança em aberto, "Pagar com Pix ou boleto": QR Code do Pix, **Pix copia e cola** e **linha digitável** (com botão de copiar) e o boleto em PDF. Mostra "O boleto está sendo gerado" enquanto o processador emite, e a lista se atualiza sozinha |
| **Cobranças** (gestão) | Faixa "Boleto e Pix ligados" com **"Emitir os boletos que faltam"**; coluna **Boleto** (gerando, emitido, com problema e o motivo, pago, cancelando); ações **2ª via** (para mandar ao morador), **Tentar de novo** e, só no simulado, **Simular pagamento** |
| **Moradores** (gestão) | O nome abre a **edição**: nome, CPF/CNPJ, e-mail e celular. Era uma pendência da etapa 11 e agora é necessária para o boleto |
| **Caixa** | A conta do gateway aparece como "Conta no gateway (Asaas)" |
| **Página do cliente** (plataforma) | Seção **Boleto e Pix**: ligar com a chave (Asaas ou simulado, sandbox ou produção), conferida no gateway na hora. Depois mostra **uma vez** o endereço e o token do webhook para cadastrar no Asaas. Também tem "Trocar a chave" e "Desligar" |

## Arquitetura (SOLID)
- **`GatewayDePagamento`** (O, L, D): a interface no pacote `financeiro.gateway`, com as implementações `asaas/GatewayAsaas`
  e `simulado/GatewaySimulado`. Um **teste de contrato** (`GatewayDePagamentoContrato`) roda contra as duas.
- **`GatewayDoCondominio`** (I): a única porta do financeiro para o painel da plataforma (módulo administração).
- **Eventos** dentro do financeiro: `CobrancasEmitidas`, `CobrancaCancelada` e `PagamentoDeCobrancaRegistrado` (este agora
  diz se veio do gateway e a tarifa). A `SolicitacaoDeBoletos` reage a eles, sem a geração e a baixa
  saberem de boleto.

## Configuração de produção
| Variável | Para quê |
|---|---|
| `CONDOMIX_COFRE_CHAVE` | 32 bytes em Base64 que cifram as chaves de API. Gere com `openssl rand -base64 32` e **não perca**: sem ela, as chaves gravadas não podem ser lidas |
| `CONDOMIX_ENDERECO_PUBLICO` | Endereço público da API (ex.: `https://api.condomix.com.br`), usado na URL do webhook |

## Como verificar

```bash
./mvnw test                  # backend: 106 testes (12 do contrato dos gateways, 9 de BoletosTests)
cd frontend && npm test      # frontend: 25 testes
```

### Verificado no navegador (banco temporário, computador e celular)
1. **Painel:** uma chave inventada no **Asaas de verdade** (sandbox) foi recusada com "A chave de API não foi aceita pelo
   Asaas...". Liguei o **simulado**, e o painel mostrou o endereço e o token do webhook.
2. **Síndico:** lancei uma multa de R$ 150 para o A-101. O boleto apareceu "Gerando boleto" e depois "Boleto com problema:
   Falta o CPF ou CNPJ de Ana Moradora". Cadastrei o CPF em Moradores, cliquei em "Tentar de novo", e a lista se
   atualizou sozinha para "Boleto emitido".
3. **Morador (celular):** em Meus débitos, "Pagar com Pix ou boleto" mostrou o Pix copia e cola e a linha digitável.
4. **Baixa:** em "Simular pagamento", a cobrança ficou "Paga" e "Pago pelo boleto/Pix". O caixa mostrou a conta
   "Recebimentos online" com **R$ 148,01**: + R$ 150,00 de multa disciplinar e − R$ 1,99 de tarifa.

## Pendências
- **Testar no sandbox do Asaas** com uma conta de testes de verdade (a implementação segue a documentação da API v3 e passa
  no contrato com as respostas gravadas). Para testar: criar a conta sandbox, ligar o condomínio com a chave e cadastrar
  o webhook com o endereço público (em desenvolvimento, por um túnel como o ngrok).
- **Aviso de pagamento em duplicidade na tela** (hoje só fica no log do servidor).
- **Mais de um servidor:** o processador de boletos supõe uma instância só. Para várias, use uma trava no banco
  (`FOR UPDATE SKIP LOCKED`).
- **Avisos ao morador** (boleto disponível, pagamento confirmado) por e-mail e push: etapa de notificações.
- **Cobrança da assinatura do Condomix pelo gateway:** etapa 16.
