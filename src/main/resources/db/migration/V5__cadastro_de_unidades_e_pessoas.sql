-- =====================================================================
-- V5 - Regras do cadastro de unidades e pessoas pelo síndico
-- =====================================================================

-- Fração ideal: parte da unidade no condomínio, entre 0 e 1 (ex.: 0,025 = 2,5%).
ALTER TABLE unidade ADD CONSTRAINT ck_unidade_fracao_ideal
    CHECK (fracao_ideal IS NULL OR (fracao_ideal > 0 AND fracao_ideal <= 1));

-- A mesma pessoa não pode ter dois vínculos em aberto do mesmo tipo com a mesma unidade.
CREATE UNIQUE INDEX ux_vinculo_unidade_em_aberto
    ON vinculo_unidade (pessoa_id, unidade_id, tipo)
    WHERE fim IS NULL;

-- Busca de pessoas pelo nome, sem diferenciar maiúsculas.
CREATE INDEX ix_pessoa_nome ON pessoa (tenant_id, lower(nome));
