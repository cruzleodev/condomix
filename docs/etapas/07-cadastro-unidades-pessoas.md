# Etapa 7 — Cadastro de unidades, pessoas e moradores

- **Branch:** `feature/cadastro-unidades-pessoas` (criada a partir de `desenvolvimento`)
- **Concluída em:** 16/11/2026

## Objetivo

O síndico monta o cadastro do condomínio sozinho: unidades, pessoas, quem mora ou é dono de cada
unidade e o convite de acesso para cada morador. Esse cadastro é a base do financeiro, onde as
cobranças vão sair por unidade.

## Unidades

| Método | Rota | Quem | O que faz |
|---|---|---|---|
| GET | `/api/unidades` | consulta¹ | Lista com os ocupantes atuais (filtro `bloco`; inativas com `incluirInativas=true`) |
| GET | `/api/unidades/resumo` | consulta¹ | Quantidade, blocos e se as frações ideais somam 100% |
| GET | `/api/unidades/{id}` | consulta¹ | Uma unidade |
| POST | `/api/unidades` | gestão² | Cadastra uma unidade |
| POST | `/api/unidades/lote` | gestão² | Cadastra várias de uma vez (ex.: um bloco inteiro). **Tudo ou nada** |
| PUT | `/api/unidades/{id}` | gestão² | Corrige bloco, identificação, tipo e fração |
| POST | `/api/unidades/{id}/desativacao` | gestão² | Desativa, **só se ninguém estiver vinculado** |
| POST | `/api/unidades/{id}/reativacao` | gestão² | Reativa |

¹ síndico, subsíndico, administradora e conselheiro. ² síndico e administradora.

- **Fração ideal:** é a parte da unidade no condomínio, um número maior que 0 e no máximo 1
  (`0.025` = 2,5%). É opcional, mas o **resumo** avisa quando as unidades ativas ainda não somam 100%.
  O rateio das despesas vai usar essa fração.
- **Descrição:** `A-101` quando há bloco, ou só `Casa 12` quando não há.

## Pessoas e vínculos

| Método | Rota | Quem | O que faz |
|---|---|---|---|
| GET | `/api/pessoas` | consulta¹ | Lista com as unidades atuais (`busca`: parte do nome ou CPF/CNPJ) |
| GET | `/api/pessoas/{id}` | consulta¹ | Uma pessoa, com o **histórico** de vínculos |
| POST | `/api/pessoas` | gestão² | Cadastra |
| PUT | `/api/pessoas/{id}` | gestão² | Corrige |
| POST | `/api/pessoas/{id}/vinculos` | gestão² | Liga a uma unidade como PROPRIETARIO, INQUILINO, MORADOR ou DEPENDENTE |
| POST | `/api/pessoas/{id}/vinculos/{vinculoId}/encerramento` | gestão² | Encerra o vínculo (mudança, venda). O histórico fica |
| POST | `/api/moradores` | gestão² | **Morador num passo só:** pessoa + vínculo + convite |

### Validações
- **CPF e CNPJ** (`DocumentoBrasileiro`, no `compartilhado`): confere os dígitos verificadores e aceita o
  **CNPJ alfanumérico**, que a Receita emite desde julho de 2026 (ex.: `12.ABC.345/01DE-35`). Grava só os
  caracteres, sem pontuação.
- **Telefone** (`TelefoneBrasileiro`): aceita qualquer formatação e grava no formato do WhatsApp (`5591999998888`).
- **E-mail** em minúsculas. **CPF/CNPJ** não se repete no mesmo condomínio.
- A mesma pessoa não pode ter dois vínculos em aberto do **mesmo tipo** com a mesma unidade.
  Coproprietários (várias pessoas donas da mesma unidade) são permitidos.

### Morador num passo só
`POST /api/moradores`:
```json
{
  "pessoa": { "nome": "João", "documento": "529.982.247-25", "email": "joao@email.com", "telefone": "(91) 99999-8888" },
  "unidadeId": "…",
  "vinculo": "INQUILINO",
  "inicio": "2026-11-01",
  "convidar": true
}
```
Cria a pessoa, faz a ligação com a unidade e, se houver e-mail, gera o convite com o papel **MORADOR**.
Se qualquer parte falhar, nada é gravado.

## "Minhas unidades" (meus dados)

`GET /api/eu/unidades` funciona para qualquer pessoa logada. Ele mostra as unidades ligadas à **pessoa**
do login no condomínio atual. Isso aplica a decisão de negócio: os dados "meus" vêm dos vínculos da
pessoa, e não do perfil MORADOR. Assim:
- o síndico profissional, que não tem unidade, recebe uma lista vazia;
- o morador-síndico vê a unidade dele.

O financeiro vai usar o mesmo caminho para "meus boletos".

## Outras mudanças
- **Correção:** o cadastro de condomínios (etapa 6) **apagava as letras** do CNPJ ao limpar a pontuação.
  Agora usa o `DocumentoBrasileiro`, que mantém as letras do CNPJ alfanumérico.
- **Cliente novo:** o CNPJ do condomínio e o documento do síndico são validados **antes** de criar o condomínio.
- **Erro 403 com o motivo** (`RespostaDeAcessoNegado`), em vez de uma resposta vazia:
  - token sem condomínio: *"Escolha um condomínio em POST /api/auth/condominio antes de usar esta rota"*;
  - suporte tentando gravar: *"O acesso de suporte é somente leitura"*;
  - demais casos: *"Seu perfil não permite esta ação"*.
- **`Permissoes`** (`acesso`): as regras `GESTAO` e `CONSULTA_DO_CADASTRO` ficam num só lugar, em vez de
  repetidas em cada controller.
- **`RestricaoDoBanco`** (`compartilhado`): transforma uma regra violada no banco numa mensagem clara.
- **Migração V5:**
  - fração ideal entre 0 e 1;
  - vínculo em aberto único por pessoa, unidade e tipo;
  - índice para buscar pessoas pelo nome.

## SOLID nesta etapa
- **S:** os casos de uso ficam separados entre escrita e leitura: `CadastroDeUnidades` × `ConsultaDeUnidades`,
  `CadastroDePessoas` × `ConsultaDePessoas`. O `CadastroDeMorador` só coordena os outros.
- **Regras nas entidades:** `Unidade` (identificação e fração), `Pessoa` (documento, e-mail, telefone) e
  `VinculoUnidade` (encerramento depois do início, uma vez só).

## Como verificar

```bash
./mvnw test
```

Resultado esperado: 59 testes passando, entre eles:
- `CadastroDoCondominioTests` (6): unidades e lote com as frações somando 100%, regras das unidades
  (duplicada, fração inválida, lote tudo ou nada), documentos validados (inclusive o CNPJ alfanumérico),
  morador num passo (convite → login → minhas unidades → encerramento → histórico), permissões e isolamento;
- `DocumentoBrasileiroTests` (5): CPF, CNPJ numérico e alfanumérico, telefone.

### No Swagger (como síndico do Residencial Exemplo)
1. **Unidades → `POST /api/unidades/lote`**:
   `{"bloco": "A", "tipo": "APARTAMENTO", "identificacoes": ["102", "201", "202"]}`
2. **Unidades → `GET /api/unidades/resumo`** mostra que as frações ainda não somam 100%.
3. **Pessoas → `POST /api/moradores`** (exemplo acima), usando o `id` de uma unidade do passo 1.
4. Na resposta, em `convite.link`, copie o que vem depois de `codigo=` e use em **Convites → `POST /api/convites/aceite`**.
5. Entre com o e-mail do morador e chame **`GET /api/eu/unidades`**.

## Pendências para etapas futuras
- **Importar unidades e moradores de uma planilha** (CSV/Excel), para condomínios grandes.
- **Morador atualizar os próprios dados** (telefone, e-mail).
- **Dar ao subsíndico permissão de cadastro** quando a convenção prever.
- **Dependentes sem login** (crianças) e veículos e animais.
