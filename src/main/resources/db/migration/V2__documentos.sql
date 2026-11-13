-- =====================================================================
-- V2 - Documentos do condomínio (convenção, regimento, atas, leis...)
-- =====================================================================

-- Busca em português que ignora acentos: "convencao" encontra "Convenção".
CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE TEXT SEARCH CONFIGURATION pt_sem_acento (COPY = pg_catalog.portuguese);
ALTER TEXT SEARCH CONFIGURATION pt_sem_acento
    ALTER MAPPING FOR hword, hword_part, word WITH unaccent, portuguese_stem;

CREATE TABLE documento (
    id               uuid           PRIMARY KEY DEFAULT uuidv7(),
    tenant_id        uuid           NOT NULL REFERENCES condominio (id),
    titulo           varchar(200)   NOT NULL,
    descricao        varchar(2000),
    categoria        varchar(30)    NOT NULL CHECK (categoria IN (
                         'CONVENCAO', 'REGIMENTO_INTERNO', 'ATA_ASSEMBLEIA', 'LEI_NORMA', 'COMUNICADO', 'OUTRO')),
    data_referencia  date,                                  -- ex.: data da assembleia; vazio se não se aplica

    -- O arquivo em si fica no armazenamento (disco em dev, S3 em produção); aqui só os dados dele.
    arquivo_nome     varchar(255)   NOT NULL,               -- nome original enviado
    arquivo_tipo     varchar(100)   NOT NULL,               -- tipo verificado pelo conteúdo, não pelo que o navegador disse
    arquivo_tamanho  bigint         NOT NULL CHECK (arquivo_tamanho > 0),
    arquivo_sha256   varchar(64)    NOT NULL,               -- impressão digital: prova que o arquivo não foi alterado
    arquivo_chave    varchar(300)   NOT NULL UNIQUE,        -- onde está no armazenamento

    enviado_por      uuid           NOT NULL REFERENCES usuario (id),
    enviado_em       timestamptz    NOT NULL DEFAULT now(),
    atualizado_em    timestamptz    NOT NULL DEFAULT now(),

    -- Documentos não são apagados de verdade (atas e convenções têm valor legal): são marcados como removidos.
    removido_em      timestamptz,
    removido_por     uuid           REFERENCES usuario (id),

    -- Índice de busca, calculado pelo banco. Título pesa mais que a descrição.
    busca            tsvector       GENERATED ALWAYS AS (
                         setweight(to_tsvector('pt_sem_acento', coalesce(titulo, '')), 'A') ||
                         setweight(to_tsvector('pt_sem_acento', coalesce(descricao, '')), 'B')
                     ) STORED,

    CHECK ((removido_em IS NULL) = (removido_por IS NULL))
);

CREATE INDEX ix_documento_tenant_categoria ON documento (tenant_id, categoria) WHERE removido_em IS NULL;
CREATE INDEX ix_documento_busca ON documento USING gin (busca);
