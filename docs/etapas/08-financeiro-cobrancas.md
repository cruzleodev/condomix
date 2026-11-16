# Etapa 8 — Financeiro: cobranças, rateio e inadimplência

- **Branch:** `feature/financeiro-cobrancas` (criada a partir de `desenvolvimento`)
- **Concluída em:** 16/11/2026

## Objetivo

O condomínio sabe **quanto cada unidade deve, quem pagou e quem está atrasado**, sem depender de banco.
Serve para os três cenários:
- a administradora gera os boletos, e o Condomix importa os dados;
- o síndico controla tudo manualmente;
- daqui a duas etapas, o Condomix gera os boletos pelo gateway.

A assinatura que o condomínio paga ao Condomix **não** entra aqui: ela é outro dinheiro, de outro módulo (etapa 11).

## Configuração da taxa

`GET`/`PUT /api/financeiro/configuracao`

| Campo | Padrão | Regra |
|---|---|---|
| `tipoRateio` | `FRACAO_IDEAL` | `IGUALITARIO`, `FRACAO_IDEAL` ou `VALOR_POR_GRUPO` |
| `diaVencimento` | 10 | entre 1 e 28 (vale para todos os meses) |
| `multaPercentual` | 2 | **até 2%** (Código Civil, art. 1.336, § 1º) |
| `jurosMensalPercentual` | 1 | entre 0 e 10% ao mês (a lei usa 1% quando a convenção não diz outro valor) |
| `fundoReservaPercentual` | 0 | % sobre a taxa ordinária |

## Os três tipos de rateio

| Tipo | Como divide | O que precisa |
|---|---|---|
| **Igual para todos** | Total ÷ número de unidades | O valor total do mês |
| **Fração ideal** | Total × fração de cada unidade (regra padrão da lei) | O valor total e a fração de **todas** as unidades ativas |
| **Valor por grupo** | Cada unidade paga o valor do seu grupo (ex.: tipo R$ 500, cobertura R$ 850) | Grupos com valor e todas as unidades num grupo |

- **Centavos sempre fecham:** a divisão usa o método do maior resto (`DistribuicaoDeCentavos`).
  R$ 1.000 para 3 unidades dá 333,34 + 333,33 + 333,33.
- **Frações que não somam 1:** se as frações ideais não somam exatamente 1, o valor é dividido
  proporcionalmente à soma (o total cobrado continua igual ao informado) e a resposta traz um **aviso**.
- **Falta de dados:** quando falta algo, a resposta diz **quais unidades** precisam de fração ideal ou de grupo.
- **Sala comercial:** cada sala é uma unidade, então quem tem 3 salas tem 3 cobranças.

Grupos: `GET/POST /api/financeiro/grupos`, `PUT /api/financeiro/grupos/{id}`,
`PUT /api/financeiro/grupos/{id}/unidades` e `DELETE /api/financeiro/grupos/{id}`.

## Geração da taxa do mês

| Método | Rota | O que faz |
|---|---|---|
| POST | `/api/financeiro/geracoes/simulacao` | **Mostra o resultado sem gravar** |
| POST | `/api/financeiro/geracoes` | Gera as cobranças de todas as unidades ativas |
| GET | `/api/financeiro/geracoes` | Gerações feitas |
| POST | `/api/financeiro/geracoes/{id}/cancelamento` | Cancela a geração e as cobranças dela (só se nenhuma foi paga) |

```json
{
  "competencia": "2026-12",
  "valorOrdinariaTotal": 30000,
  "vencimento": "2026-12-10",
  "taxasExtras": [{ "descricao": "Pintura da fachada", "valorTotal": 9000, "rateio": "FRACAO_IDEAL" }]
}
```

Composição de cada cobrança (os itens aparecem na resposta):
1. **Taxa ordinária**, pela regra de rateio.
2. **Fundo de reserva**: o % sobre a ordinária. O total do fundo é distribuído para fechar no centavo.
3. **Taxas extras** do mês, cada uma rateada igual ou pela fração ideal.
4. **Desconto de mandato** (síndico, subsíndico...): o % definido no mandato (etapa 5), sobre a ordinária,
   **proporcional aos dias do mês** em que o mandato valeu, e só na unidade indicada.

Outras regras:
- Só **uma geração válida por mês**. Para refazer, cancele a anterior.
- Unidades com total zero (ex.: isenção de 100% sem fundo de reserva) **não recebem cobrança** e aparecem em `isentas`.
- Cada cobrança guarda o **responsável** (quem paga): o proprietário; se não houver, o inquilino; se não houver, o morador.
  Ele será o pagador do boleto na etapa do gateway.

## Cobranças

| Método | Rota | Quem | O que faz |
|---|---|---|---|
| GET | `/api/cobrancas` | consulta¹ | Lista com filtros (`competencia`, `status`, `unidadeId`), com valor atualizado |
| GET | `/api/cobrancas/{id}` | consulta¹ | Uma cobrança |
| GET | `/api/cobrancas/inadimplencia` | consulta¹ | Unidades com cobranças vencidas: dias de atraso, principal, multa, juros e total |
| POST | `/api/cobrancas` | gestão² | Avulsa: multa disciplinar, aluguel de espaço, consumo, taxa extra, outro |
| POST | `/api/cobrancas/{id}/pagamento` | gestão² | **Baixa manual** (sem valor: usa o valor com encargos na data) |
| POST | `/api/cobrancas/{id}/estorno` | gestão² | Desfaz uma baixa feita por engano |
| POST | `/api/cobrancas/{id}/cancelamento` | gestão² | Cancela uma cobrança em aberto, com motivo |
| POST | `/api/cobrancas/importacao` | gestão² | Importa a planilha da administradora |
| GET | `/api/eu/cobrancas` | qualquer logado | **Meus débitos**: unidades em que sou proprietário ou inquilino |

¹ síndico, subsíndico, administradora e conselheiro. ² síndico e administradora.

### Multa e juros
Não ficam gravados: são **calculados na data**, com as regras do momento da emissão:
- **multa:** uma vez, % do valor;
- **juros:** simples, pro rata dia (juros ao mês ÷ 30 × dias de atraso).

Exemplo: R$ 500 com 15 dias de atraso → multa R$ 10,00 + juros R$ 2,50 = **R$ 512,50**.
Depois de paga, a cobrança "congela" os encargos na data do pagamento.

### Importação da administradora
Planilha CSV, separada por `;` ou `,`, em UTF-8 ou Windows-1252 (o formato do Excel em português):
```
unidade;competência;vencimento;valor;situação;data_pagamento;valor_pago;descricao
A-101;10/2026;10/10/2026;1.234,56;PAGA;09/10/2026;1.234,56;Taxa outubro
A-102;10/2026;10/10/2026;980,00;ABERTA;;;
```
- Aceita datas `dd/mm/aaaa` ou `aaaa-mm-dd`, valores `1.234,56` ou `1234.56`, e a unidade escrita
  como `A-101`, `a 101` ou `A101`. O cabeçalho pode ter acentos.
- **Tudo ou nada:** com erro em qualquer linha, nada é importado, e a resposta diz **a linha e o motivo**.
- Não importa uma cobrança que já existe na mesma competência para a unidade.

## Organização (SOLID)

| Subpacote | Conteúdo |
|---|---|
| `dominio` | `Cobranca` (encargos, baixa, estorno, cancelamento), `ConfiguracaoDaCobranca`, grupos, `Dinheiro` |
| `rateio` | `CalculoDoRateio` (interface) + `RateioIgualitario`, `RateioPorFracaoIdeal`, `ValorPorGrupo`; `DistribuicaoDeCentavos` |
| `aplicacao` | `GeracaoMensal`, `BaixaDeCobrancas`, `EmissaoAvulsa`, `ConsultaDeCobrancas`, `ImportacaoDeCobrancas`, `ConfiguracaoDeCobrancas` |

- **O:** cada regra de rateio é uma implementação de `CalculoDoRateio`. Uma regra nova não mexe nas outras.
- **I / D:** o financeiro lê o cadastro por uma API pequena do `acesso`, a `CadastroParaCobranca`: unidades
  ativas com responsável, descontos de mandato e unidades do pagador. Ele não conhece entidades nem repositórios do `acesso`.
- A cobrança avulsa (`EmissaoAvulsa`) já está pronta para o aluguel de espaços da etapa de reservas.

## Como verificar

```bash
./mvnw test
```

Resultado esperado: 74 testes passando, entre eles:
- `FinanceiroTests` (9):
  - os três rateios, com fundo de reserva e taxa extra;
  - uma geração por mês, e cancelar para gerar de novo;
  - desconto proporcional do mandato;
  - pagamento atrasado com multa e juros, inadimplência, estorno e cancelamento;
  - meus débitos;
  - importação tudo ou nada;
  - permissões e isolamento;
- `CobrancaTests` (4) e `DistribuicaoDeCentavosTests` (2).

Os testes encontraram e corrigiram, antes do commit:
- o fundo de reserva perdia 1 centavo por arredondamento;
- o cabeçalho com "competência" não era reconhecido;
- os filtros vazios da lista quebravam no PostgreSQL.

### No Swagger (como síndico do Residencial Exemplo)
1. **Unidades → `PUT /api/unidades/{id}`**: defina a `fracaoIdeal` de cada unidade (ex.: 0.25 para 4 unidades iguais).
   A outra opção é mudar o rateio para `IGUALITARIO` em **Financeiro: configuração → `PUT /api/financeiro/configuracao`**.
2. **Financeiro: geração mensal → `POST /api/financeiro/geracoes/simulacao`**: `{"competencia": "2026-12", "valorOrdinariaTotal": 5000}`.
3. Se o resultado estiver certo, use o mesmo corpo em **`POST /api/financeiro/geracoes`**.
4. **Financeiro: cobranças → `GET /api/cobrancas`** e **`POST /api/cobrancas/{id}/pagamento`** com `{"forma": "PIX"}`.
5. Como `morador@condomix.dev`: **`GET /api/eu/cobrancas`**.

## Pendências para etapas futuras
- **Caixa, despesas e balancete** (etapa 9). Os pagamentos registrados aqui vão alimentar o caixa.
- **Boletos e Pix com baixa automática** (etapa 10).
- **Consumo individual** (água e gás por unidade) com leitura de medidores.
- **Acordos e parcelamento** de dívidas e **desconto por pagamento em dia**.
- **Uma cobrança só para quem tem várias unidades** (ex.: 3 salas no mesmo documento).
- **Correção monetária** das dívidas antigas, quando a convenção prever.
