-- =====================================================================
-- V4 - Administração da plataforma: administrador, preços dos planos, convites e suporte
-- =====================================================================

-- Administrador da plataforma (o dono do Condomix e sua equipe). Não pertence a nenhum condomínio.
ALTER TABLE usuario ADD COLUMN admin_plataforma boolean NOT NULL DEFAULT false;

-- Preços dos planos, definidos pelo administrador. Vazios enquanto não forem definidos.
ALTER TABLE plano ADD COLUMN preco_mensal    numeric(10, 2) CHECK (preco_mensal >= 0);
ALTER TABLE plano ADD COLUMN preco_semestral numeric(10, 2) CHECK (preco_semestral >= 0);
ALTER TABLE plano ADD COLUMN preco_anual     numeric(10, 2) CHECK (preco_anual >= 0);

-- Convite para uma pessoa do condomínio criar o login (ou ligar o login que já tem).
-- Só a impressão digital (SHA-256) do código do convite é guardada: quem lê o banco não consegue usá-lo.
CREATE TABLE convite (
    id            uuid          PRIMARY KEY DEFAULT uuidv7(),
    tenant_id     uuid          NOT NULL REFERENCES condominio (id),
    pessoa_id     uuid          NOT NULL,
    email         varchar(150)  NOT NULL,
    perfil        varchar(20)   CHECK (perfil IN ('ADMINISTRADORA', 'MORADOR', 'PORTARIA')),   -- vazio: o papel vem de um mandato
    codigo_hash   varchar(64)   NOT NULL UNIQUE,
    expira_em     timestamptz   NOT NULL,
    aceito_em     timestamptz,
    aceito_por    uuid          REFERENCES usuario (id),
    cancelado_em  timestamptz,
    criado_por    uuid          REFERENCES usuario (id),
    criado_em     timestamptz   NOT NULL DEFAULT now(),

    FOREIGN KEY (pessoa_id, tenant_id) REFERENCES pessoa (id, tenant_id),
    CHECK ((aceito_em IS NULL) = (aceito_por IS NULL)),
    CHECK (aceito_em IS NULL OR cancelado_em IS NULL)
);
CREATE INDEX ix_convite_tenant_pessoa ON convite (tenant_id, pessoa_id);

-- Registro de cada acesso do administrador aos dados de um condomínio (modo suporte). LGPD: quem, quando, por quê.
CREATE TABLE acesso_suporte (
    id                uuid          PRIMARY KEY DEFAULT uuidv7(),
    administrador_id  uuid          NOT NULL REFERENCES usuario (id),
    condominio_id     uuid          NOT NULL REFERENCES condominio (id),
    motivo            varchar(500)  NOT NULL CHECK (length(trim(motivo)) >= 10),
    inicio            timestamptz   NOT NULL,
    expira_em         timestamptz   NOT NULL,
    CHECK (expira_em > inicio)
);
CREATE INDEX ix_acesso_suporte_condominio ON acesso_suporte (condominio_id, inicio DESC);
