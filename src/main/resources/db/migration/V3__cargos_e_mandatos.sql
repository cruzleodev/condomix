-- =====================================================================
-- V3 - Cargos e mandatos (síndico, subsíndico e conselho)
--
-- A partir daqui, os cargos eleitos (SINDICO, SUBSINDICO, CONSELHEIRO) vêm dos mandatos vigentes.
-- perfil_acesso fica só com os papéis sem mandato: MORADOR, ADMINISTRADORA e PORTARIA.
-- =====================================================================

-- Permite a regra "só um síndico por vez" direto no banco (restrição de exclusão com intervalos de datas).
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Chaves compostas (id, tenant_id): garantem que as referências do mandato são do MESMO condomínio.
ALTER TABLE pessoa    ADD CONSTRAINT uq_pessoa_id_tenant    UNIQUE (id, tenant_id);
ALTER TABLE unidade   ADD CONSTRAINT uq_unidade_id_tenant   UNIQUE (id, tenant_id);
ALTER TABLE documento ADD CONSTRAINT uq_documento_id_tenant UNIQUE (id, tenant_id);

-- O login da pessoa naquele condomínio (vazio enquanto ela não tiver acesso ao sistema).
ALTER TABLE pessoa ADD COLUMN usuario_id uuid REFERENCES usuario (id);
ALTER TABLE pessoa ADD CONSTRAINT uq_pessoa_usuario UNIQUE (tenant_id, usuario_id);

CREATE TABLE mandato (
    id                       uuid           PRIMARY KEY DEFAULT uuidv7(),
    tenant_id                uuid           NOT NULL REFERENCES condominio (id),
    pessoa_id                uuid           NOT NULL,
    cargo                    varchar(20)    NOT NULL CHECK (cargo IN ('SINDICO', 'SUBSINDICO', 'CONSELHEIRO')),
    inicio                   date           NOT NULL,              -- data da posse
    fim_previsto             date           NOT NULL,              -- fim do mandato conforme a eleição
    encerrado_em             date,                                 -- último dia efetivo; vazio enquanto não encerrado
    motivo_encerramento      varchar(20)    CHECK (motivo_encerramento IN ('SUCESSAO', 'RENUNCIA', 'DESTITUICAO', 'OUTRO')),
    ata_eleicao_id           uuid,                                 -- documento com a ata da eleição
    ata_encerramento_id      uuid,                                 -- ata da destituição, carta de renúncia...
    desconto_percentual      numeric(5, 2)  CHECK (desconto_percentual BETWEEN 0 AND 100),
    unidade_beneficiada_id   uuid,                                 -- unidade que recebe o desconto
    registrado_por           uuid           REFERENCES usuario (id),
    criado_em                timestamptz    NOT NULL DEFAULT now(),
    atualizado_em            timestamptz    NOT NULL DEFAULT now(),

    FOREIGN KEY (pessoa_id, tenant_id)              REFERENCES pessoa (id, tenant_id),
    FOREIGN KEY (unidade_beneficiada_id, tenant_id) REFERENCES unidade (id, tenant_id),
    FOREIGN KEY (ata_eleicao_id, tenant_id)         REFERENCES documento (id, tenant_id),
    FOREIGN KEY (ata_encerramento_id, tenant_id)    REFERENCES documento (id, tenant_id),

    CHECK (fim_previsto >= inicio),
    CHECK (encerrado_em IS NULL OR encerrado_em >= inicio),
    CHECK ((encerrado_em IS NULL) = (motivo_encerramento IS NULL)),
    CHECK ((desconto_percentual IS NULL) = (unidade_beneficiada_id IS NULL)),

    -- Síndico e subsíndico: nunca dois ao mesmo tempo no mesmo condomínio.
    CONSTRAINT ex_mandato_cargo_unico EXCLUDE USING gist (
        tenant_id WITH =,
        cargo WITH =,
        daterange(inicio, encerrado_em, '[]') WITH &&
    ) WHERE (cargo IN ('SINDICO', 'SUBSINDICO'))
);
CREATE INDEX ix_mandato_tenant ON mandato (tenant_id, cargo);
CREATE INDEX ix_mandato_pessoa ON mandato (pessoa_id);


-- ---------------------------------------------------------------------
-- Conversão dos dados existentes
-- ---------------------------------------------------------------------

-- 1. Liga as pessoas já associadas a um login.
UPDATE pessoa p SET usuario_id = pa.usuario_id
FROM (SELECT DISTINCT tenant_id, pessoa_id, usuario_id FROM perfil_acesso WHERE pessoa_id IS NOT NULL) pa
WHERE p.id = pa.pessoa_id AND p.tenant_id = pa.tenant_id;

-- 2. Quem tinha perfil SINDICO ou CONSELHO sem pessoa ganha uma pessoa (nome provisório = e-mail).
INSERT INTO pessoa (tenant_id, nome, email, usuario_id)
SELECT DISTINCT pa.tenant_id, u.email, u.email, u.id
FROM perfil_acesso pa
JOIN usuario u ON u.id = pa.usuario_id
WHERE pa.perfil IN ('SINDICO', 'CONSELHO')
  AND NOT EXISTS (SELECT 1 FROM pessoa p WHERE p.tenant_id = pa.tenant_id AND p.usuario_id = pa.usuario_id);

-- 3. Os perfis SINDICO e CONSELHO viram mandatos de 2 anos a partir de quando foram criados.
INSERT INTO mandato (tenant_id, pessoa_id, cargo, inicio, fim_previsto)
SELECT pa.tenant_id, p.id,
       CASE pa.perfil WHEN 'SINDICO' THEN 'SINDICO' ELSE 'CONSELHEIRO' END,
       pa.criado_em::date,
       (pa.criado_em::date + interval '2 years' - interval '1 day')::date
FROM perfil_acesso pa
JOIN pessoa p ON p.tenant_id = pa.tenant_id AND p.usuario_id = pa.usuario_id
WHERE pa.perfil IN ('SINDICO', 'CONSELHO') AND pa.ativo;

-- 4. perfil_acesso passa a aceitar só os papéis sem mandato.
DELETE FROM perfil_acesso WHERE perfil IN ('SINDICO', 'CONSELHO');
ALTER TABLE perfil_acesso DROP CONSTRAINT perfil_acesso_perfil_check;
ALTER TABLE perfil_acesso ADD CONSTRAINT perfil_acesso_perfil_check
    CHECK (perfil IN ('ADMINISTRADORA', 'MORADOR', 'PORTARIA'));
