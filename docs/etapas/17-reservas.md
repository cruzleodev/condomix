# Etapa 17 — Reservas: espaços, agenda, regras e aluguel

- **Branch:** `feature/reservas` (criada a partir de `desenvolvimento`)
- **Concluída em:** 19/11/2026

## Objetivo

Os moradores reservam pelo app os espaços comuns (salão de festas, churrasqueira, quadra...). A gestão cadastra os
espaços com os horários, o aluguel e as regras, e acompanha a agenda. O aluguel vira cobrança da unidade sozinho.

## Decisões (tomadas com você no início da etapa)

| Tema | Decisão |
|---|---|
| Aprovação | **A reserva vale na hora**, se passar nas regras e o horário estiver livre. Não há aprovação do síndico; a gestão pode cancelar depois |
| Aluguel | **Cobrança avulsa sozinha** ("Aluguel de espaço") na unidade, vencendo no dia da reserva. Com o gateway ligado (etapa 15), ela já sai com boleto e Pix |
| Regras | As quatro, com os números configurados em cada espaço: **antecedência** (mínima e máxima), **limite por mês** por unidade, **bloqueio de inadimplente** e **prazo para cancelar** sem custo |
| Plano | **Liberado para todos os planos** por ora. A separação Essencial × Completo fica para depois |

## Como funciona

### Espaços e horários
- Cada espaço tem um ou mais **horários** que podem ser reservados. Por exemplo, um só "Dia inteiro", ou "Almoço"
  (10h–16h) e "Noite" (18h–23h).
  - Os horários de um espaço **não se sobrepõem**. Assim, cada horário de um dia tem no máximo uma reserva.
  - Por enquanto, o horário termina no mesmo dia.
- A reserva guarda o horário e o valor **de quando foi feita**: mudar o espaço depois não altera as reservas existentes.
- Desativar um espaço impede reservas novas; as já feitas continuam valendo.

### Reservar
1. O morador reserva para uma unidade em que é **proprietário, inquilino ou morador** (dependentes não reservam). A
   **gestão** (síndico ou administradora) reserva para qualquer unidade.
2. Horário de hoje que já começou não pode ser reservado.
3. Todas as **regras** (`RegraDeReserva`) são aplicadas, e cada recusa vem com o motivo em português:
   - **Antecedência:** "precisa ser reservado com pelo menos 2 dias de antecedência: a partir de 21/11/2026";
   - **Limite por mês:** "a unidade já tem 1 reserva deste espaço em 11/2026, o limite do condomínio";
   - **Inadimplência:** "a unidade tem cobrança vencida em aberto. Regularize em Meus débitos para poder reservar".
4. O espaço fica **travado** durante a reserva (`SELECT ... FOR UPDATE`). Assim, duas pessoas pedindo o mesmo
   horário ao mesmo tempo passam uma de cada vez, e a segunda recebe "Este horário já está reservado". O banco também
   tem um índice único para garantir isso.
5. Com aluguel, a reserva e a cobrança avulsa são gravadas **na mesma transação**: ou as duas, ou nenhuma.

### Cancelar
- Pode cancelar até o dia da reserva. Uma reserva que já aconteceu não pode mais ser cancelada.
- **No prazo do espaço** (ex.: até 2 dias antes), a cobrança do aluguel também é cancelada.
  - Se o aluguel já foi pago, a cobrança fica como está, e o morador é avisado para combinar a devolução com o síndico.
- **Fora do prazo**, a reserva é cancelada, mas o aluguel continua devido ("Cancelada (aluguel devido)").
- A **gestão** pode **dispensar o aluguel** fora do prazo (ex.: o espaço ficou interditado).
- O morador só cancela as reservas das suas unidades.

## Telas

| Tela | O que mudou |
|---|---|
| **Reservas** (todos, aba nova no celular) | Os espaços com o aluguel, os horários e as regras em frases curtas. **Minhas reservas** (próximas, com "Cancelar a reserva") e **Anteriores e canceladas** |
| **Agenda do espaço** | Mês a mês, cada dia com os horários: **livre** (botão), **sua reserva**, **reservado** (a gestão vê qual unidade) e **indisponível**. Ao escolher um horário: a unidade, a observação, o aluguel e até quando dá para cancelar sem custo, e o botão **Confirmar a reserva** |
| **Espaços e reservas** (gestão, menu e atalho na Gestão) | Lista dos espaços com Editar e Desativar/Reativar, e o formulário com nome, aluguel, descrição, capacidade, os **horários** (adicionar e tirar) e as **regras**. Também **Reservas dos próximos 60 dias**, com Cancelar (e "Dispensar o aluguel" fora do prazo) |
| **Barra do celular** | Entrou **Reservas**. O Balancete (já estava no Início) saiu da barra do morador. Para a gestão, Documentos saiu da barra e continua no Início ("Documentos recentes → Ver todos") |

## Arquitetura (SOLID)
- **`RegraDeReserva`** (O, L): três implementações (`Antecedencia`, `LimitePorMes`, `BloqueioDeInadimplente`).
  - O serviço recebe uma `List<RegraDeReserva>`: uma regra nova é só uma classe nova.
  - O teste de contrato `RegraDeReservaContrato` roda contra as três.
- **Portas pequenas** (I) entre módulos: `CadastroParaReservas` (acesso: as unidades de quem reserva) e
  `CobrancasParaReservas` (financeiro: cobrar e cancelar o aluguel e dizer se a unidade está inadimplente). O módulo
  de reservas não expõe nada.
- **Horas como texto "HH:MM"** no banco (`HoraDoDia`). O projeto grava datas em UTC (`hibernate.jdbc.time_zone`), e o
  Hibernate converteria uma coluna `time`: 23h viraria 2h da manhã.
- `PeriodoDoEspaco` e `RegrasDoEspaco` são classes embutidas na entidade; a API devolve `record`s próprios.

## Como verificar

```bash
./mvnw test                  # backend: 131 testes (7 de ReservasTests, 6 do contrato das regras)
cd frontend && npm test      # frontend: 32 testes
```

### Verificado no navegador (banco temporário, computador e celular)
1. **Síndico (computador):** em Gestão → Espaços e reservas, cadastrei o **Salão de festas**: R$ 150,00, até 60 pessoas,
   horários Almoço (10h–16h) e Noite (18h–23h), cancelamento sem custo até 2 dias antes.
2. **Morador (celular):**
   - Reservas mostrou o salão com as regras.
   - Na agenda, o almoço de hoje aparecia "indisponível", porque já tinha começado.
   - Reservei o domingo à noite: "Reservado... O aluguel de R$ 150,00 já está em Meus débitos", e a cobrança apareceu lá,
     vencendo no dia da reserva.
   - Cancelei: "Reserva cancelada. A cobrança do aluguel também foi cancelada", e Meus débitos ficou vazio.
3. **Fora do prazo:**
   - O síndico reservou a sexta (faltando 1 dia) para a unidade A-101, e a confirmação avisou que faltam menos de 2 dias.
   - Em Reservas dos próximos 60 dias, cancelei marcando "Dispensar o aluguel", e a cobrança foi cancelada.

## Pendências
- **Separar por plano** (reservas só no Completo), quando você quiser.
- **Horário que vira a noite** (ex.: 20h às 2h) e **bloqueio de datas** pela gestão (manutenção, evento do condomínio).
- **Aprovação do síndico** por espaço, se algum condomínio pedir. Hoje toda reserva vale na hora.
- **Lista de convidados** e **termo de uso** do espaço.
