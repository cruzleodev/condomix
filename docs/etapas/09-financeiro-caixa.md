# Etapa 9 — Financeiro: caixa, despesas e balancete

- **Branch:** `feature/financeiro-caixa` (criada a partir de `desenvolvimento`)
- **Concluída em:** 16/11/2026

## Objetivo

Saber **quanto o condomínio tem, onde está e para onde foi**, e fazer a prestação de contas do mês
(balancete) para o conselho e os moradores. Esta etapa completa o financeiro do condomínio junto com a etapa 8.

## Contas

São os lugares onde o dinheiro fica: conta corrente, poupança, aplicação (ex.: fundo de reserva) e caixa físico.

| Método | Rota | Quem | O que faz |
|---|---|---|---|
| GET | `/api/financeiro/contas` | consulta¹ | Contas com o **saldo de hoje** |
| POST | `/api/financeiro/contas` | gestão² | Cadastra (nome, tipo, banco, agência, número, **saldo inicial** e a data dele) |
| PUT | `/api/financeiro/contas/{id}` | gestão² | Corrige nome, tipo e dados bancários |
| POST | `/api/financeiro/contas/{id}/recebimento-padrao` | gestão² | Define onde caem os pagamentos das cobranças |
| POST | `/api/financeiro/contas/{id}/desativacao` | gestão² | Desativa (o histórico continua) |

- **Saldo inicial:** é o saldo na data em que o condomínio começa a usar o Condomix. Lançamentos só a partir dessa data.
- **Conta de recebimento:** a primeira conta cadastrada passa a receber os pagamentos. Se o síndico registrar um
  pagamento sem ter conta nenhuma, o sistema cria sozinho a **"Conta principal"**, com saldo zero.

## Plano de contas

`GET /api/financeiro/categorias`. O plano padrão é criado no primeiro acesso:

| Receitas | Despesas |
|---|---|
| Taxa condominial, Fundo de reserva, Taxas extras, Multas e juros por atraso, Multas disciplinares, Aluguel de espaços, Consumo, Rendimentos, Outras receitas | Pessoal e encargos, Água e esgoto, Energia elétrica, Gás, Manutenção, Limpeza, Segurança e portaria, Administração, Seguros, Impostos e taxas, Tarifas bancárias, Obras, Outras despesas |

O condomínio pode criar categorias próprias (`POST /api/financeiro/categorias`) e desativá-las. As do sistema
não podem ser desativadas, porque são usadas nos lançamentos automáticos.

## Lançamentos

| Método | Rota | Quem | O que faz |
|---|---|---|---|
| POST | `/api/financeiro/lancamentos` | gestão² | Despesa ou receita avulsa (a categoria tem que ser da mesma natureza) |
| POST | `/api/financeiro/transferencias` | gestão² | Move dinheiro entre contas (não é receita nem despesa) |
| POST | `/api/financeiro/lancamentos/{id}/cancelamento` | gestão² | Cancela (numa transferência, as duas pontas) |
| GET | `/api/financeiro/lancamentos?de=&ate=&contaId=` | consulta¹ | Extrato, **inclusive os cancelados** (auditoria) |

Nada é apagado: um erro se corrige cancelando, com motivo.

## Pagamentos de cobranças entram sozinhos no caixa

Quando o síndico registra o pagamento de uma cobrança (etapa 8), o caixa recebe **um lançamento por categoria**:

| Na cobrança | No caixa |
|---|---|
| Taxa ordinária (menos o desconto de mandato) | Taxa condominial |
| Fundo de reserva | Fundo de reserva |
| Taxa extra | Taxas extras |
| Aluguel de espaço, multa disciplinar, consumo, outro | Categoria correspondente |
| O que foi pago **a mais** que o valor | Multas e juros por atraso |
| O que foi pago **a menos** (perdão) | É tirado das maiores parcelas primeiro |

Exemplo: uma cobrança de R$ 1.100 (R$ 1.000 de taxa + R$ 100 de fundo), paga com 10 dias de atraso por
R$ 1.125,67, entra no caixa como **Taxa R$ 1.000 + Fundo R$ 100 + Multas e juros R$ 25,67**.

- **Estorno do pagamento:** cancela esses lançamentos.
- **Cancelamento direto:** esses lançamentos não podem ser cancelados à mão. É preciso estornar o pagamento na cobrança.
- **Como funciona:** por eventos (`PagamentoDeCobrancaRegistrado` / `Estornado`), na **mesma transação**. Se a
  entrada no caixa falhar, a baixa também não acontece.
- **Cobranças importadas** da administradora **não** entram no caixa. Elas são histórico, e o saldo inicial
  da conta já representa esse dinheiro.

## Balancete do mês

`GET /api/financeiro/balancete?competencia=2026-11` é aberto a **todos do condomínio**, por transparência
na prestação de contas. Mostra só totais, sem dados pessoais:
- por conta: saldo anterior, entradas, saídas e saldo final;
- receitas e despesas por categoria, com os totais e o **resultado do mês** (as transferências não contam);
- saldo total final;
- **inadimplência**: cobranças vencidas até o fim do mês.

O extrato detalhado, com fornecedores e descrições, fica para a gestão, o subsíndico e o conselho.

¹ síndico, subsíndico, administradora e conselheiro. ² síndico e administradora.

## Organização (SOLID)

- `PlanoDeContas` (contas e categorias), `MovimentacaoDeCaixa` (lançar, transferir, cancelar),
  `ConsultaDoCaixa` (saldos e extrato), `Balancete` e `EntradaNoCaixa` (reage aos pagamentos).
- **O:** cada `TipoDeItem` da cobrança sabe a sua categoria no caixa, sem `switch`.
- **Desacoplamento:** as cobranças não conhecem o caixa; só publicam eventos.

## Como verificar

```bash
./mvnw test
```

Resultado esperado: 79 testes passando. Os 5 do `CaixaTests` cobrem:
- o plano de contas padrão e as contas;
- despesa, transferência e balancete (a transferência não muda o total), e o cancelamento de transferência;
- o pagamento de cobrança entrando por categoria (com multa e juros), a "Conta principal" automática e o estorno;
- o pagamento a menor (perdão);
- permissões (o morador vê o balancete, mas não o extrato) e o isolamento entre condomínios.

### No Swagger (como síndico do Residencial Exemplo)
1. **Financeiro: caixa e balancete → `POST /api/financeiro/contas`**:
   `{"nome": "Conta corrente", "tipo": "CONTA_CORRENTE", "saldoInicial": 15000, "dataSaldoInicial": "2026-11-01"}`
2. **`GET /api/financeiro/categorias`** para pegar o `id` de "Energia elétrica".
3. **`POST /api/financeiro/lancamentos`**:
   `{"tipo": "DESPESA", "contaId": "...", "categoriaId": "...", "valor": 850, "descricao": "Conta de luz", "favorecido": "Equatorial"}`
4. Pague uma cobrança da etapa 8 (`POST /api/cobrancas/{id}/pagamento`) e veja o saldo em **`GET /api/financeiro/contas`**.
5. **`GET /api/financeiro/balancete?competencia=2026-11`**. Funciona também como `morador@condomix.dev`.

## Pendências para etapas futuras
- **Comprovantes anexados** aos lançamentos (nota fiscal, recibo), num armazenamento próprio, separado dos documentos públicos.
- **Previsão orçamentária** (orçado × realizado) e **conciliação bancária** (importar o extrato OFX do banco).
- **Balancete em PDF**, assinado pelo síndico, publicado nos documentos.
- **Aprovação de despesas** pelo conselho acima de um valor.
