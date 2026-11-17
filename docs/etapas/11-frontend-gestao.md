# Etapa 11 — Frontend parte 2: computador para todos e gestão do condomínio

- **Branch:** `feature/frontend-gestao` (criada a partir de `desenvolvimento`)
- **Concluída em:** 17/11/2026

## Objetivo

1. **O app funciona bem no computador para todos os usuários**, e não só no celular.
2. O síndico (e quem acompanha a gestão) passa a **administrar o cadastro pelo app**: unidades, moradores,
   convites e mandatos, sem precisar do Swagger.

## Layout responsivo (todos os usuários)

O mesmo app se adapta ao tamanho da tela:

| | Celular (até 767 px) | Computador (a partir de 768 px) |
|---|---|---|
| Navegação | Barra fixa embaixo, com 5 itens | **Menu lateral** com todos os itens e a seção "Gestão" |
| Conteúdo | Uma coluna | Até 1152 px de largura, cartões em 2 colunas |
| Listas grandes | Cartões | **Tabelas** (componente `Tabela`: a mesma lista vira tabela ou cartões) |
| Formulários | Um campo por linha | Duas colunas |

Isso vale para todas as telas, inclusive as do morador (início, débitos, documentos, balancete).

### Navegação por papel (`layout/navegacao.ts`)
- **Morador:** Início, Débitos, Documentos, Balancete e Conta.
- **Gestão e conselho** (síndico, subsíndico, administradora, conselheiro): no celular, a aba **Gestão** entra no lugar
  do Balancete (que continua acessível pelo Início e pela Gestão). No computador, o menu ganha a seção
  Gestão: Unidades, Moradores, Cargos e mandatos.
- **As rotas de gestão são protegidas por papel** (`RotaComPapel`): quem não tem o papel e digita o endereço volta ao
  início. A API continua sendo a barreira real (403).

## Telas de gestão

| Tela | Quem vê | O que dá para fazer (síndico e administradora) |
|---|---|---|
| **Gestão** | gestão e conselho | Atalhos e o aviso quando as frações ideais não somam 100% |
| **Unidades** | gestão e conselho | Resumo (ativas, blocos, % das frações), filtro por bloco, **nova unidade**, **cadastro em lote** (com gerador de sequência, ex.: 101 a 104), editar (fração em %), desativar e reativar |
| **Moradores** | gestão e conselho | Busca por nome ou CPF/CNPJ, CPF e celular formatados, **cadastro de morador num passo** (pessoa + unidade + convite) e **convidar** quem ainda não tem acesso |
| **Cargos e mandatos** | gestão e conselho | Quem ocupa cada cargo hoje (vigente, a vencer, prorrogado, futuro), avisos, histórico, **registrar eleição** (com desconto opcional na taxa) e **encerrar** (renúncia, destituição) |

O subsíndico e o conselheiro **consultam**; os botões de cadastro só aparecem para o síndico e a administradora.

### Convites
Ao cadastrar o morador (ou clicar em "Convidar"), aparece o **link do convite com o botão "Copiar link"**.
Enquanto não houver envio por e-mail (etapa de notificações), o síndico repassa o link pelo WhatsApp.

## Ajustes encontrados ao usar o app
- **Cache:** a lista de unidades da tela Unidades (que inclui as desativadas) usava a mesma chave de cache das
  listas de escolha dos formulários. Um formulário poderia oferecer uma unidade desativada. Agora são chaves separadas.
- **Backend:** os avisos de mandato mostravam a data como `2027-01-19`. Agora mostram `19/01/2027`.
- O `package-lock.json` passou a ter o nome certo do projeto (`condomix-frontend`).

## Como verificar

```bash
./mvnw test                  # backend: 80 testes
cd frontend && npm test      # frontend: 15 testes (navegação por papel e formatações novas)
```

### Verificado no navegador
Num **banco temporário**, com os dados de exemplo criados do zero (o seu banco não foi alterado):

1. **Computador (1280 px), como síndico:** menu lateral com Gestão e aviso de mandato "a vencer" no início.
   Em Unidades, a sequência de 101 a 104 virou B-101 a B-104, a tabela apareceu e o resumo avisou que as frações estão em 0%.
2. **Moradores:** cadastrei "Bruno Teste" (CPF, celular, B-101, inquilino) e o link do convite apareceu com "Copiar link".
3. **Celular:** abri o link do convite, criei a senha, o login abriu com o e-mail preenchido e o Bruno entrou vendo
   "B-101 · Inquilino". A barra dele não tem Gestão, e o endereço `/gestao/unidades` o devolve ao início.
4. **Celular, como síndico:** registrei o Bruno como conselheiro. Os mandatos mostram o Bruno "vigente" e o síndico
   "a vencer", com o aviso. Nenhum erro no console.

### Para você testar
Com a API rodando, `cd frontend && npm run dev`, abra http://localhost:5173 e entre como `sindico@condomix.dev`.
Para ver o modo celular no Chrome: ⌥⌘I e depois ⇧⌘M.

## Pendências
- **Financeiro do síndico** nas telas (taxa, geração, cobranças, baixa, inadimplência, caixa): etapa 12.
- **Painel do administrador da plataforma:** etapa 13.
- Editar dados de uma pessoa e encerrar vínculos pela tela (a API já tem; entram junto com o financeiro).
