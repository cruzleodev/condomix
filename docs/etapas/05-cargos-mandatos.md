# Etapa 5 — Cargos e mandatos

- **Branch:** `feature/cargos-mandatos` (criada a partir de `desenvolvimento`)
- **Concluída em:** 14/11/2026

## Objetivo

Tratar síndico, subsíndico e conselho como **cargos eleitos com mandato**, e não como permissões
fixas. Cobre o síndico que é morador, o síndico profissional, a passagem para o sucessor, o mandato
vencido sem eleição, a renúncia, a destituição e o desconto na taxa condominial.

## A mudança principal

**Antes:** "síndico" era um perfil gravado na tabela `perfil_acesso`.

**Agora:** os papéis vêm de duas fontes, calculadas a cada login (`PapeisDoUsuario`):

| Fonte | Papéis | Onde fica |
|---|---|---|
| Mandato com acesso na data de hoje | SINDICO, SUBSINDICO, CONSELHEIRO | tabela `mandato` |
| Perfil gravado (sem mandato) | MORADOR, ADMINISTRADORA, PORTARIA | tabela `perfil_acesso` |

Exemplos:
- **Morador que é síndico:** o token sai com `["MORADOR", "SINDICO"]`.
- **Síndico profissional:** o token sai só com `["SINDICO"]`. Com um único login, ele atende vários condomínios.
- **Quando o mandato acaba,** o papel some sozinho no login seguinte. Ninguém precisa lembrar de tirar a permissão.

A pessoa passou a ter um campo `usuario_id`, que liga a pessoa do condomínio ao login dela.

## Situação de um mandato

A situação é calculada para a data de hoje e não fica gravada (`Mandato.situacaoEm`):

| Situação | Quando | Dá os poderes do cargo? | Aviso |
|---|---|---|---|
| `FUTURO` | A posse ainda não aconteceu | Não | — |
| `VIGENTE` | Em andamento | Sim | — |
| `A_VENCER` | Faltam 90 dias ou menos | Sim | "O mandato termina em N dias. Convoque a assembleia…" |
| `PRORROGADO` | O prazo passou e ninguém tomou posse | **Sim** | "Mandato vencido em … Prorrogado até a posse do sucessor…" |
| `ENCERRADO` | Sucessão, renúncia ou destituição | Não | — |

`PRORROGADO` aplica a decisão de negócio "prorrogar com aviso".

## Regras

- **Duração máxima de 2 anos** (Código Civil, arts. 1.347 e 1.356).
- **Um síndico e um subsíndico por vez.** O próprio banco garante isso com uma restrição de exclusão
  (`ex_mandato_cargo_unico`), que proíbe períodos sobrepostos. Conselheiros podem ser vários.
- **Sucessão automática:** quando o novo síndico ou subsíndico é registrado, o mandato do
  antecessor é encerrado no dia anterior à posse (motivo `SUCESSAO`).
- **Encerramento antecipado:** renúncia, destituição ou outro motivo, com data e, se houver, a ata.
  A sucessão não pode ser registrada assim: ela acontece cadastrando o novo mandato.
- **Nada é apagado:** o histórico mostra quem ocupou cada cargo em cada período.
- **Ata da eleição e do encerramento:** são documentos do módulo de documentos. O banco garante que
  a ata pertence ao **mesmo condomínio**, usando chaves compostas `(id, tenant_id)`.
- **Desconto na taxa:** percentual (0 a 100%) e a unidade beneficiada, informados juntos. Fica
  guardado no mandato para o módulo financeiro aplicar.
- **Quem registra:** síndico ou administradora. **Quem consulta:** todos do condomínio. Os moradores têm
  direito de saber quem ocupa os cargos.

## Endpoints

| Método | Rota | Quem pode | O que faz |
|---|---|---|---|
| GET | `/api/mandatos` | todos | Histórico, mais recentes primeiro. Com `somenteComAcesso=true`, só os atuais |
| GET | `/api/mandatos/{id}` | todos | Um mandato, com situação e aviso |
| POST | `/api/mandatos` | síndico, administradora | Registra uma eleição |
| POST | `/api/mandatos/{id}/encerramento` | síndico, administradora | Renúncia, destituição ou outro motivo |
| GET | `/api/pessoas` | síndico, subsíndico, administradora | Pessoas do condomínio e suas unidades, para escolher quem ocupa o cargo |

Exemplo de registro de eleição:

```json
{
  "pessoaId": "…",
  "cargo": "SUBSINDICO",
  "inicio": "2026-12-01",
  "fimPrevisto": "2027-11-30",
  "ataEleicaoId": "…",
  "descontoPercentual": 50,
  "unidadeBeneficiadaId": "…"
}
```

## Conversão dos dados existentes (migração V3)

1. As pessoas já associadas a um login recebem o `usuario_id`.
2. Quem tinha perfil SINDICO ou CONSELHO sem pessoa ganha uma pessoa, com o e-mail como nome provisório.
3. Esses perfis viram mandatos de 2 anos, a partir da data em que foram criados.
4. A tabela `perfil_acesso` passa a aceitar só MORADOR, ADMINISTRADORA e PORTARIA.

No banco de desenvolvimento, a conversão foi conferida: o síndico continua com acesso aos 2
condomínios, agora por mandato.

## Organização (SOLID)

- **S:** `PapeisDoUsuario` só calcula papéis, e o `AutenticacaoService` só cuida do login.
  `RegistroDeMandatos` (escrita) e `ConsultaDeMandatos` (leitura) são classes separadas.
- **O:** cada `SituacaoMandato` define o próprio aviso, sem `switch`.
- **Regras na entidade:** `Mandato` valida a duração, o encerramento e calcula a situação.
- **Erros comuns** passaram para o `compartilhado`: `NaoEncontradoException` e `RegraDeNegocioException` (400/409).

## Dados de exemplo (perfil dev, banco novo)

- `sindico@condomix.dev` virou **Carlos Síndico Profissional**:
  - mandato **a vencer** no Residencial Exemplo;
  - mandato **prorrogado** no Centro Comercial Exemplo.
- `morador@condomix.dev` (Ana) passou a ter a pessoa ligada ao login.

Para ver esse cenário no seu banco atual, que foi convertido pela V3, é preciso recriar o banco de
desenvolvimento. Isso apaga os dados de teste (veja o README). Sem recriar, o síndico aparece com um
mandato **vigente** de 2 anos.

## Como verificar

```bash
./mvnw test
```

Resultado esperado: 41 testes passando. Os 10 do `MandatosTests` cobrem:
- morador-síndico;
- sucessão;
- prorrogação com aviso;
- aviso de "a vencer";
- renúncia registrada pela administradora;
- síndico profissional em 2 condomínios;
- regras (2 anos, conflito, morador sem permissão);
- desconto e conselho;
- isolamento entre condomínios (inclusive a ata de outro condomínio);
- proibição de gravar cargo como perfil.

Os testes ganharam o `CenarioDeTeste`, que monta condomínio, usuário, perfil, mandato e login em
uma linha cada.

### No Swagger

1. Entre como `sindico@condomix.dev`, escolha o **Residencial Exemplo** e autorize com o token novo.
2. Em **Cargos e mandatos → `GET /api/mandatos`**, veja o mandato atual e a situação dele.
3. Em **Pessoas → `GET /api/pessoas`**, copie o `id` da **Ana Moradora**.
4. Em **`POST /api/mandatos`**, registre a Ana como `SUBSINDICO`, com `inicio` hoje e `fimPrevisto` daqui a 1 ano.
5. Entre como `morador@condomix.dev`. Em **`GET /api/eu`**, os `perfis` agora são `["MORADOR", "SUBSINDICO"]`.

## Pendências para etapas futuras

- **Política "suspender"** para mandato vencido, configurável por condomínio. Hoje vale só "prorrogar".
- **Avisos por notificação** (e-mail, push, WhatsApp) aos 90, 60 e 30 dias. Hoje o aviso aparece na consulta.
- **Cadastro de pessoas e convites** (ligar uma pessoa a um login): etapa de administração.
- **Subsíndico substituir o síndico** nas permissões (ex.: publicar documentos) quando a convenção prever.
- **Aplicar o desconto** do mandato nas cobranças: etapa do financeiro.
