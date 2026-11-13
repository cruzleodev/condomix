-- =====================================================================
-- V1 - Cadastro base do Condomix
--
-- Convenções:
--   * Chaves primárias UUID v7 (ordenadas no tempo). A aplicação gera o id;
--     o DEFAULT uuidv7() serve para inserts manuais (DBeaver, scripts).
--   * tenant_id = id do condomínio dono do registro. Toda tabela com dados
--     de um condomínio tem essa coluna, com índice.
--   * Datas e horas em timestamptz (gravadas em UTC).
--   * Enums gravados como texto, validados por CHECK.
-- =====================================================================


-- ---------------------------------------------------------------------
-- Módulo plataforma: planos, condomínios (tenants) e assinaturas
-- ---------------------------------------------------------------------

-- Planos vendidos pelo Condomix. Tabela global (não pertence a um condomínio).
CREATE TABLE plano (
    id          uuid         PRIMARY KEY DEFAULT uuidv7(),
    codigo      varchar(30)  NOT NULL UNIQUE,
    nome        varchar(80)  NOT NULL,
    descricao   varchar(500),
    tipo        varchar(20)  NOT NULL CHECK (tipo IN ('BASE', 'ADICIONAL')),
    ativo       boolean      NOT NULL DEFAULT true,
    criado_em   timestamptz  NOT NULL DEFAULT now()
);

-- Quais módulos do sistema cada plano libera.
CREATE TABLE plano_modulo (
    plano_id    uuid         NOT NULL REFERENCES plano (id) ON DELETE CASCADE,
    modulo      varchar(30)  NOT NULL CHECK (modulo IN ('DOCUMENTOS', 'FINANCEIRO', 'RESERVAS', 'WHATSAPP')),
    PRIMARY KEY (plano_id, modulo)
);

-- O condomínio é o tenant: cada cliente do Condomix.
CREATE TABLE condominio (
    id             uuid          PRIMARY KEY DEFAULT uuidv7(),
    nome           varchar(150)  NOT NULL,
    cnpj           varchar(14)   UNIQUE,                  -- só dígitos
    tipo           varchar(30)   NOT NULL CHECK (tipo IN ('RESIDENCIAL_VERTICAL', 'RESIDENCIAL_HORIZONTAL', 'COMERCIAL', 'MISTO')),
    email          varchar(150),
    telefone       varchar(20),
    cep            varchar(8),
    logradouro     varchar(150),
    numero         varchar(20),
    complemento    varchar(80),
    bairro         varchar(80),
    cidade         varchar(80),
    uf             varchar(2),
    ativo          boolean       NOT NULL DEFAULT true,
    criado_em      timestamptz   NOT NULL DEFAULT now(),
    atualizado_em  timestamptz   NOT NULL DEFAULT now()
);

-- Assinatura de um plano por um condomínio. Um condomínio tem uma assinatura
-- de plano BASE e pode ter assinaturas de planos ADICIONAIS (ex.: WhatsApp).
CREATE TABLE assinatura (
    id             uuid         PRIMARY KEY DEFAULT uuidv7(),
    tenant_id      uuid         NOT NULL REFERENCES condominio (id),
    plano_id       uuid         NOT NULL REFERENCES plano (id),
    periodicidade  varchar(20)  NOT NULL CHECK (periodicidade IN ('MENSAL', 'SEMESTRAL', 'ANUAL')),
    status         varchar(20)  NOT NULL CHECK (status IN ('TESTE', 'ATIVA', 'SUSPENSA', 'CANCELADA')),
    inicio         date         NOT NULL,
    fim            date,
    criado_em      timestamptz  NOT NULL DEFAULT now(),
    atualizado_em  timestamptz  NOT NULL DEFAULT now(),
    CHECK (fim IS NULL OR fim >= inicio)
);
CREATE INDEX ix_assinatura_tenant ON assinatura (tenant_id);
-- O mesmo plano não pode estar ativo duas vezes no mesmo condomínio.
CREATE UNIQUE INDEX ux_assinatura_plano_vigente
    ON assinatura (tenant_id, plano_id)
    WHERE status IN ('TESTE', 'ATIVA', 'SUSPENSA');


-- ---------------------------------------------------------------------
-- Módulo acesso: unidades, pessoas, usuários e perfis
-- ---------------------------------------------------------------------

-- Apartamento, casa, sala ou loja.
CREATE TABLE unidade (
    id             uuid           PRIMARY KEY DEFAULT uuidv7(),
    tenant_id      uuid           NOT NULL REFERENCES condominio (id),
    bloco          varchar(30),                            -- torre, bloco ou quadra; vazio se não houver
    identificacao  varchar(30)    NOT NULL,                -- "101", "Casa 12", "Loja 3"
    tipo           varchar(20)    NOT NULL CHECK (tipo IN ('APARTAMENTO', 'CASA', 'SALA', 'LOJA', 'OUTRO')),
    fracao_ideal   numeric(9, 6),                          -- usada no rateio das despesas
    ativo          boolean        NOT NULL DEFAULT true,
    criado_em      timestamptz    NOT NULL DEFAULT now(),
    atualizado_em  timestamptz    NOT NULL DEFAULT now(),
    UNIQUE NULLS NOT DISTINCT (tenant_id, bloco, identificacao)
);
CREATE INDEX ix_unidade_tenant ON unidade (tenant_id);

-- Pessoas ligadas ao condomínio: proprietários, inquilinos, moradores, síndico...
-- Tem ou não login: o login fica em "usuario".
CREATE TABLE pessoa (
    id             uuid          PRIMARY KEY DEFAULT uuidv7(),
    tenant_id      uuid          NOT NULL REFERENCES condominio (id),
    nome           varchar(150)  NOT NULL,
    documento      varchar(14),                           -- CPF ou CNPJ, só dígitos
    email          varchar(150),
    telefone       varchar(20),                           -- formato E.164 (ex.: 5591999999999), usado no WhatsApp
    criado_em      timestamptz   NOT NULL DEFAULT now(),
    atualizado_em  timestamptz   NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, documento)
);
CREATE INDEX ix_pessoa_tenant ON pessoa (tenant_id);

-- Vínculo entre pessoa e unidade (uma pessoa pode ter várias unidades e vice-versa).
CREATE TABLE vinculo_unidade (
    id          uuid         PRIMARY KEY DEFAULT uuidv7(),
    tenant_id   uuid         NOT NULL REFERENCES condominio (id),
    pessoa_id   uuid         NOT NULL REFERENCES pessoa (id),
    unidade_id  uuid         NOT NULL REFERENCES unidade (id),
    tipo        varchar(20)  NOT NULL CHECK (tipo IN ('PROPRIETARIO', 'INQUILINO', 'MORADOR', 'DEPENDENTE')),
    inicio      date         NOT NULL DEFAULT current_date,
    fim         date,
    criado_em   timestamptz  NOT NULL DEFAULT now(),
    CHECK (fim IS NULL OR fim >= inicio)
);
CREATE INDEX ix_vinculo_unidade_tenant ON vinculo_unidade (tenant_id);
CREATE INDEX ix_vinculo_unidade_pessoa ON vinculo_unidade (pessoa_id);
CREATE INDEX ix_vinculo_unidade_unidade ON vinculo_unidade (unidade_id);

-- Login. É global (sem tenant_id): a mesma pessoa pode acessar mais de um
-- condomínio com um único e-mail (ex.: administradora, dono de imóveis em dois condomínios).
CREATE TABLE usuario (
    id              uuid          PRIMARY KEY DEFAULT uuidv7(),
    email           varchar(150)  NOT NULL,
    senha_hash      varchar(100)  NOT NULL,
    ativo           boolean       NOT NULL DEFAULT true,
    ultimo_acesso   timestamptz,
    criado_em       timestamptz   NOT NULL DEFAULT now(),
    atualizado_em   timestamptz   NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_usuario_email ON usuario (lower(email));

-- O que o usuário pode fazer em cada condomínio.
CREATE TABLE perfil_acesso (
    id          uuid         PRIMARY KEY DEFAULT uuidv7(),
    tenant_id   uuid         NOT NULL REFERENCES condominio (id),
    usuario_id  uuid         NOT NULL REFERENCES usuario (id),
    pessoa_id   uuid         REFERENCES pessoa (id),        -- a pessoa que este login representa no condomínio
    perfil      varchar(20)  NOT NULL CHECK (perfil IN ('SINDICO', 'ADMINISTRADORA', 'CONSELHO', 'MORADOR', 'PORTARIA')),
    ativo       boolean      NOT NULL DEFAULT true,
    criado_em   timestamptz  NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, usuario_id, perfil)
);
CREATE INDEX ix_perfil_acesso_usuario ON perfil_acesso (usuario_id);


-- ---------------------------------------------------------------------
-- Dados iniciais: planos (preços entram na etapa de cobrança da assinatura)
-- ---------------------------------------------------------------------
INSERT INTO plano (codigo, nome, descricao, tipo) VALUES
    ('ESSENCIAL', 'Essencial', 'Documentos e financeiro.',                         'BASE'),
    ('COMPLETO',  'Completo',  'Documentos, financeiro e reservas.',               'BASE'),
    ('WHATSAPP',  'WhatsApp',  'Adicional: atendimento e notificações por WhatsApp.', 'ADICIONAL');

INSERT INTO plano_modulo (plano_id, modulo)
SELECT p.id, m.modulo
FROM plano p
JOIN (VALUES
    ('ESSENCIAL', 'DOCUMENTOS'),
    ('ESSENCIAL', 'FINANCEIRO'),
    ('COMPLETO',  'DOCUMENTOS'),
    ('COMPLETO',  'FINANCEIRO'),
    ('COMPLETO',  'RESERVAS'),
    ('WHATSAPP',  'WHATSAPP')
) AS m (codigo, modulo) ON m.codigo = p.codigo;
