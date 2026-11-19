-- =====================================================================
-- V8 - Administrador: bloqueio de login, fim do acesso de suporte e histórico da assinatura
-- =====================================================================

-- Bloqueio de um login pelo administrador da plataforma (ex.: suspeita de invasão da conta).
-- Bloqueado, o usuário não entra, e os tokens que ele já tinha deixam de valer.
ALTER TABLE usuario ADD COLUMN bloqueado_em     timestamptz;
ALTER TABLE usuario ADD COLUMN motivo_bloqueio  varchar(500);
ALTER TABLE usuario ADD COLUMN bloqueado_por    uuid REFERENCES usuario (id);
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_bloqueio
    CHECK ((bloqueado_em IS NULL) = (motivo_bloqueio IS NULL) AND (bloqueado_em IS NULL OR NOT ativo));

-- O administrador encerra o acesso de suporte antes do prazo. Daí em diante o token de suporte não vale mais.
ALTER TABLE acesso_suporte ADD COLUMN encerrado_em timestamptz;
ALTER TABLE acesso_suporte ADD CONSTRAINT ck_acesso_suporte_encerramento
    CHECK (encerrado_em IS NULL OR encerrado_em >= inicio);

-- Histórico da assinatura de cada cliente: cada mudança, quando e quem fez. Dado da plataforma (sem tenant_id,
-- como acesso_suporte): só o administrador da plataforma lê, para todos os condomínios.
CREATE TABLE evento_assinatura (
    id             uuid          PRIMARY KEY DEFAULT uuidv7(),
    condominio_id  uuid          NOT NULL REFERENCES condominio (id),
    momento        timestamptz   NOT NULL,
    tipo           varchar(30)   NOT NULL CHECK (tipo IN ('CLIENTE_NOVO', 'TROCA_DE_PLANO', 'MUDANCA_DE_SITUACAO', 'SUSPENSAO', 'REATIVACAO')),
    plano_id       uuid          REFERENCES plano (id),
    periodicidade  varchar(20)   CHECK (periodicidade IN ('MENSAL', 'SEMESTRAL', 'ANUAL')),
    status         varchar(20)   CHECK (status IN ('TESTE', 'ATIVA', 'SUSPENSA', 'CANCELADA')),
    feito_por      uuid          REFERENCES usuario (id)          -- vazio: feito pelo sistema
);
CREATE INDEX ix_evento_assinatura_condominio ON evento_assinatura (condominio_id, momento DESC);

-- O que já existia: a primeira assinatura de cada condomínio é a entrada do cliente (sempre em teste);
-- as seguintes são trocas de plano. A situação das trocas antigas só é conhecida se ainda estiverem vigentes.
INSERT INTO evento_assinatura (condominio_id, momento, tipo, plano_id, periodicidade, status)
SELECT a.tenant_id, a.criado_em,
       CASE WHEN a.criado_em = min(a.criado_em) OVER (PARTITION BY a.tenant_id) THEN 'CLIENTE_NOVO' ELSE 'TROCA_DE_PLANO' END,
       a.plano_id, a.periodicidade,
       CASE WHEN a.criado_em = min(a.criado_em) OVER (PARTITION BY a.tenant_id) THEN 'TESTE'
            WHEN a.status = 'CANCELADA' THEN NULL ELSE a.status END
FROM assinatura a
JOIN plano p ON p.id = a.plano_id AND p.tipo = 'BASE';
