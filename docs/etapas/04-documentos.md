# Etapa 4 — Documentos

- **Branch:** `feature/documentos` (criada a partir de `feature/autenticacao`)
- **Concluída em:** 13/11/2026

## Objetivo

O síndico publica convenção, regimento, atas, leis e comunicados. Todos do condomínio consultam,
buscam e baixam os documentos pelo celular.

## Endpoints

| Método | Rota | Quem pode | O que faz |
|---|---|---|---|
| GET | `/api/documentos/categorias` | todos | Lista as categorias, com o nome para exibir |
| GET | `/api/documentos` | todos | Lista ou busca, com paginação (`categoria`, `busca`, `pagina`, `tamanho`) |
| GET | `/api/documentos/{id}` | todos | Dados de um documento |
| GET | `/api/documentos/{id}/arquivo` | todos | Abre ou baixa o arquivo |
| POST | `/api/documentos` | síndico, administradora | Publica (formulário multipart: `titulo`, `categoria`, `descricao`, `dataReferencia`, `arquivo`) |
| PUT | `/api/documentos/{id}` | síndico, administradora | Corrige título, categoria, descrição e data (JSON) |
| DELETE | `/api/documentos/{id}` | síndico, administradora | Remove da lista (o documento continua guardado) |

"Todos" quer dizer qualquer perfil do condomínio selecionado no token. Quem é de outro condomínio
recebe 404: a API nem revela que o documento existe.

### Categorias

| Código | Nome exibido |
|---|---|
| `CONVENCAO` | Convenção |
| `REGIMENTO_INTERNO` | Regimento interno |
| `ATA_ASSEMBLEIA` | Ata de assembleia |
| `LEI_NORMA` | Leis e normas |
| `COMUNICADO` | Comunicado |
| `OUTRO` | Outros |

### Arquivos aceitos

PDF, JPG, PNG, DOCX e XLSX, com até **20 MB**.

O tipo é confirmado pelos **primeiros bytes do conteúdo**, e não só pela extensão: um `virus.exe`
renomeado para `ata.pdf` é recusado. O tipo gravado é o verificado, nunca o que o navegador informou.

## Busca

`GET /api/documentos?busca=...` procura no título (peso maior) e na descrição, usando o próprio
PostgreSQL (full-text search):

- **Ignora acentos:** `convencao` encontra "Convenção".
- **Entende variações das palavras em português:** `reformas` encontra "reforma".
- **Aceita a sintaxe de buscadores:** `"assembleia geral"` (frase exata), `obras -piscina` (exclui uma palavra).
- Os resultados vêm ordenados por relevância.

Como funciona: a migração V2 cria a configuração de busca `pt_sem_acento` (português + extensão
`unaccent`). A coluna `busca` da tabela é calculada pelo banco a cada gravação e tem um índice GIN.

## Organização do módulo

Primeiro módulo organizado em subpacotes, como pede o [CLAUDE.md](../../CLAUDE.md):

| Subpacote | Conteúdo |
|---|---|
| (raiz, público) | `Categoria` e o evento `DocumentoPublicado` |
| `dominio` | `Documento`, `DadosDoDocumento`, `ArquivoDoDocumento`, `TipoDeArquivo` |
| `aplicacao` | Casos de uso: `PublicacaoDeDocumentos`, `ConsultaDeDocumentos`, `ManutencaoDeDocumentos` |
| `persistencia` | `DocumentoRepository`, com a busca |
| `armazenamento` | `ArmazenamentoDeArquivos` (interface) e `ArmazenamentoEmDisco` |
| `web` | `DocumentoController` e as respostas |

### SOLID nesta etapa

- **S:** um caso de uso por classe. O controller só cuida do HTTP.
- **O / D:** os casos de uso dependem da interface `ArmazenamentoDeArquivos`. Para trocar o disco
  pelo S3 basta criar outra implementação, sem mexer no resto.
- **L:** o `ContratoArmazenamentoDeArquivos` é um teste que toda implementação de armazenamento
  precisa passar. Hoje roda contra o disco; amanhã, também contra o S3.
- **D:** os casos de uso recebem um `ArquivoRecebido`, e não o `MultipartFile` do Spring. O controller faz a conversão.
- **Evento:** `DocumentoPublicado` é publicado ao publicar um documento. Na etapa de notificações,
  o módulo de comunicação vai usá-lo para avisar os moradores, sem depender do módulo de documentos.

## Decisões

- **Arquivos fora do banco:** o banco guarda só os dados do arquivo (nome, tipo, tamanho, SHA-256
  e chave). O conteúdo vai para o armazenamento, numa pasta por condomínio (`{tenant_id}/{uuid}`).
  - Em desenvolvimento, fica em `./dados/arquivos`. Essa pasta está no `.gitignore`.
  - Em produção, o caminho vem de `ARMAZENAMENTO_DIRETORIO`, até a troca pelo S3.
- **SHA-256 do arquivo:** é a "impressão digital" do conteúdo. Serve para provar que uma ata não foi
  alterada depois de publicada.
- **Remoção só marca o documento** (`removido_em`, `removido_por`). Atas e convenções têm valor
  legal, então nada se perde. O Hibernate esconde os removidos de todas as consultas (`@SQLRestriction`).
- **Sem arquivo órfão:** se o banco recusar a gravação, o arquivo recém-guardado é apagado.
- **Para trocar o arquivo,** remova o documento e publique de novo. A edição muda só os dados.

## Armadilha descoberta: SQL puro não passa pelo filtro de condomínio

O filtro automático do `@TenantId` vale para as consultas do Hibernate, mas **não** para as consultas
em SQL puro (`nativeQuery = true`). A busca precisa de SQL puro, por causa do full-text. Por isso,
nela, o `tenant_id` e o `removido_em` estão escritos explicitamente. O teste
`outroCondominioNaoVeNemBaixa` garante o isolamento. A regra foi acrescentada ao CLAUDE.md.

## Como verificar

```bash
./mvnw test
```

Resultado esperado: 31 testes passando, entre eles:
- `DocumentosTests` (7): publicação e download, permissões, arquivo falso, busca, isolamento entre
  condomínios, edição e remoção, categorias;
- `ArmazenamentoEmDiscoTests` (5): o contrato do armazenamento.

### No Swagger

1. Entre como `sindico@condomix.dev`, escolha o **Residencial Exemplo** e autorize com o token novo
   (veja a etapa 3).
2. Em **Documentos → `POST /api/documentos`**, clique em **Try it out**. Preencha `titulo` e
   `categoria`, clique em **Escolher arquivo** no campo `arquivo` e selecione um PDF. Depois clique em **Execute**.
3. Entre como `morador@condomix.dev` e autorize com o token dele.
   - Em **`GET /api/documentos`**, preencha o campo `busca` (ex.: `convencao`) e execute.
   - Em **`GET /api/documentos/{id}/arquivo`**, use o `id` da listagem e clique em **Download file**
     para baixar o arquivo.

## Pendências para etapas futuras

- **Armazenamento no S3** (ou compatível) para produção.
- **Buscar dentro do conteúdo dos PDFs** (extrair o texto ao publicar). Hoje a busca olha só o título e a descrição.
- **Visibilidade restrita** (ex.: documento só para o conselho).
- **Avisar os moradores** quando sair documento novo: o evento `DocumentoPublicado` já existe.
- **Antivírus nos arquivos enviados** antes de ir para produção.
