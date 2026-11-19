# Etapa 12 — Frontend parte 3: financeiro do síndico

- **Branch:** `feature/frontend-financeiro` (criada a partir de `desenvolvimento`)
- **Concluída em:** 19/11/2026

## Objetivo

O síndico faz pelo app todo o financeiro das etapas 8 e 9, no computador e no celular:
1. define como a taxa é cobrada;
2. simula e gera a taxa do mês;
3. recebe pagamentos;
4. acompanha a inadimplência;
5. controla o caixa.

## Telas (seção Gestão)

| Tela | O que tem |
|---|---|
| **Taxa do mês** | **Como a taxa é cobrada**: rateio, dia de vencimento, multa (até 2%), juros e fundo de reserva. **Grupos e valores**, quando o rateio é por grupo, com a escolha das unidades de cada grupo. **Gerar a taxa de um mês**: mês, valor total, vencimento opcional e taxas extras (obra, rateio), cada uma dividida igual ou pela fração ideal. **Simular** mostra a tabela com o valor de cada unidade, item por item, os avisos e as unidades isentas, e **"Confirmar e gerar"** grava. **Gerações feitas** lista as gerações, com opção de cancelar |
| **Cobranças** | Aba **Cobranças**: filtros por mês e situação, valor de hoje (com multa e juros), **Receber** (data, forma, valor recebido e observação; mostra o valor atualizado), **Cancelar**, **Estornar**, **Cobrança avulsa** (multa disciplinar, aluguel de espaço, consumo...) e **Importar planilha** da administradora, com o modelo das colunas. Aba **Inadimplência**: total em atraso hoje e, por unidade, o responsável, os dias de atraso, o valor, a multa e os juros e o total |
| **Caixa** | **Saldo total** e cartão de cada conta (com a conta de recebimentos marcada); **lançar despesa ou receita** (só mostra as categorias da natureza certa); **transferir** entre contas; **nova conta**; **nova categoria**; **extrato do mês** por conta (cancelados riscados; os lançamentos de cobrança aparecem marcados e só saem estornando a cobrança) |

- **Permissões:** o subsíndico e o conselho **consultam** tudo. Os botões de ação só aparecem para o síndico e a administradora.
- **Valores em reais:** podem ser digitados como `1.234,56` ou `1234.56` (`lerReais`).

## Também no backend: datas no formato brasileiro nas mensagens

Usando o app, apareceram datas no formato técnico em textos para o usuário, como "Taxa condominial 2026-12"
e "A taxa de 2026-12 já foi gerada". Agora:
- o novo `FormatoBrasileiro` (no `compartilhado`) formata `19/01/2027` e `12/2026`;
- foi aplicado a **10 mensagens**: descrição da taxa do mês e da importação, conflitos de geração e de importação,
  conta, vínculo e mandato;
- um teste confere a descrição "Taxa condominial MM/AAAA".

## Como verificar

```bash
./mvnw test                  # backend: 80 testes
cd frontend && npm test      # frontend: 17 testes
```

### Verificado no navegador (banco temporário, computador e celular)
1. **Taxa do mês:** mudei para rateio igual com 10% de fundo e simulei dez/2026 com R$ 5.000 mais uma pintura de
   R$ 1.000 dividida igual. Cada unidade ficou com R$ 1.250 + R$ 125 + R$ 250 = **R$ 1.625**, total **R$ 6.500**, com
   o aviso das unidades sem responsável. Confirmei e as 4 cobranças foram geradas.
2. **Cobranças:** lancei uma multa avulsa de R$ 200 vencida há 30 dias. A **inadimplência** mostrou
   **R$ 206,00** (multa de 2% = R$ 4 e juros de 1% por 30 dias = R$ 2). Ao receber, o formulário mostrou o valor
   atualizado, e a cobrança ficou "Paga".
3. **Caixa:** a "Conta principal" foi criada sozinha com R$ 206, e o extrato separou **Multas disciplinares R$ 200**
   e **Multas e juros por atraso R$ 6**. Lancei uma conta de luz de R$ 50 e o saldo foi para **R$ 156**.
4. **Celular:** cobranças e caixa em cartões, com as ações em cada cartão. Nenhum erro no console.

## Pendências
- **Painel do administrador da plataforma:** etapa 13.
- **Editar dados de uma pessoa e encerrar vínculos** pela tela de Moradores.
- **Exportar** o extrato e o balancete (PDF ou planilha).
- **Recibo** do pagamento para o morador.
