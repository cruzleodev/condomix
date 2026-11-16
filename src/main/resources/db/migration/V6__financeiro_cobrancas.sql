-- =====================================================================
-- V6 - Financeiro do condomínio: configuração da taxa, rateio e cobranças
--
-- Valores em reais (numeric 12,2). Percentuais em numeric(5,2): 2.00 = 2%.
-- =====================================================================

-- Como o condomínio cobra a taxa. Uma linha por condomínio.
CREATE TABLE configuracao_cobranca (
    id                         uuid           PRIMARY KEY DEFAULT uuidv7(),
    tenant_id                  uuid           NOT NULL UNIQUE REFERENCES condominio (id),
    tipo_rateio                varchar(20)    NOT NULL CHECK (tipo_rateio IN ('IGUALITARIO', 'FRACAO_IDEAL', 'VALOR_POR_GRUPO')),
    dia_vencimento             smallint       NOT NULL CHECK (dia_vencimento BETWEEN 1 AND 28),
    multa_percentual           numeric(5, 2)  NOT NULL CHECK (multa_percentual BETWEEN 0 AND 2),      -- Código Civil, art. 1.336, § 1º
    juros_mensal_percentual    numeric(5, 2)  NOT NULL CHECK (juros_mensal_percentual BETWEEN 0 AND 10),
    fundo_reserva_percentual   numeric(5, 2)  NOT NULL CHECK (fundo_reserva_percentual BETWEEN 0 AND 100),
    atualizado_em              timestamptz    NOT NULL DEFAULT now()
);

-- Grupos para o rateio "valor por grupo" (ex.: apartamento tipo, cobertura, sala).
CREATE TABLE grupo_de_unidades (
    id             uuid            PRIMARY KEY DEFAULT uuidv7(),
    tenant_id      uuid            NOT NULL REFERENCES condominio (id),
    nome           varchar(80)     NOT NULL,
    valor_mensal   numeric(12, 2)  NOT NULL CHECK (valor_mensal >= 0),
    criado_em      timestamptz     NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, nome),
    UNIQUE (id, tenant_id)
);

-- Cada unidade em no máximo um grupo.
CREATE TABLE grupo_de_unidades_membro (
    id          uuid  PRIMARY KEY DEFAULT uuidv7(),
    unidade_id  uuid  NOT NULL UNIQUE,
    grupo_id    uuid  NOT NULL,
    tenant_id   uuid  NOT NULL REFERENCES condominio (id),
    FOREIGN KEY (unidade_id, tenant_id) REFERENCES unidade (id, tenant_id),
    FOREIGN KEY (grupo_id, tenant_id) REFERENCES grupo_de_unidades (id, tenant_id) ON DELETE CASCADE
);
CREATE INDEX ix_grupo_membro_grupo ON grupo_de_unidades_membro (grupo_id);

-- Uma geração da taxa do mês (todas as unidades de uma vez).
CREATE TABLE geracao_de_cobranca (
    id                     uuid            PRIMARY KEY DEFAULT uuidv7(),
    tenant_id              uuid            NOT NULL REFERENCES condominio (id),
    competencia            date            NOT NULL CHECK (extract(day FROM competencia) = 1),   -- sempre o dia 1º do mês
    tipo_rateio            varchar(20)     NOT NULL,
    valor_ordinaria_total  numeric(12, 2),
    gerada_por             uuid            REFERENCES usuario (id),
    gerada_em              timestamptz     NOT NULL DEFAULT now(),
    cancelada_em           timestamptz
);
-- Só uma geração válida por mês.
CREATE UNIQUE INDEX ux_geracao_competencia ON geracao_de_cobranca (tenant_id, competencia) WHERE cancelada_em IS NULL;

-- O que uma unidade deve pagar. Origem: geração mensal, lançamento avulso (multa, aluguel...) ou importação.
CREATE TABLE cobranca (
    id                       uuid            PRIMARY KEY DEFAULT uuidv7(),
    tenant_id                uuid            NOT NULL REFERENCES condominio (id),
    unidade_id               uuid            NOT NULL,
    responsavel_pessoa_id    uuid,                                      -- quem paga (pagador do boleto)
    origem                   varchar(20)     NOT NULL CHECK (origem IN ('GERACAO_MENSAL', 'AVULSA', 'IMPORTACAO')),
    geracao_id               uuid            REFERENCES geracao_de_cobranca (id),
    competencia              date            CHECK (competencia IS NULL OR extract(day FROM competencia) = 1),
    descricao                varchar(200)    NOT NULL,
    vencimento               date            NOT NULL,
    valor                    numeric(12, 2)  NOT NULL CHECK (valor > 0),
    multa_percentual         numeric(5, 2)   NOT NULL,                  -- regras do momento da emissão
    juros_mensal_percentual  numeric(5, 2)   NOT NULL,
    status                   varchar(20)     NOT NULL CHECK (status IN ('ABERTA', 'PAGA', 'CANCELADA')),
    pago_em                  date,
    valor_pago               numeric(12, 2)  CHECK (valor_pago IS NULL OR valor_pago >= 0),
    forma_pagamento          varchar(20)     CHECK (forma_pagamento IN ('PIX', 'BOLETO', 'TRANSFERENCIA', 'DINHEIRO', 'CARTAO', 'OUTRO')),
    observacao_pagamento     varchar(500),
    baixa_registrada_por     uuid            REFERENCES usuario (id),
    cancelada_em             timestamptz,
    motivo_cancelamento      varchar(500),
    criada_por               uuid            REFERENCES usuario (id),
    criado_em                timestamptz     NOT NULL DEFAULT now(),
    atualizado_em            timestamptz     NOT NULL DEFAULT now(),

    FOREIGN KEY (unidade_id, tenant_id) REFERENCES unidade (id, tenant_id),
    FOREIGN KEY (responsavel_pessoa_id, tenant_id) REFERENCES pessoa (id, tenant_id),
    CHECK ((status = 'PAGA') = (pago_em IS NOT NULL)),
    CHECK ((status = 'PAGA') = (valor_pago IS NOT NULL)),
    CHECK ((status = 'CANCELADA') = (cancelada_em IS NOT NULL))
);
CREATE INDEX ix_cobranca_tenant_status ON cobranca (tenant_id, status, vencimento);
CREATE INDEX ix_cobranca_unidade ON cobranca (unidade_id, vencimento DESC);
CREATE INDEX ix_cobranca_geracao ON cobranca (geracao_id);

-- A composição da cobrança: taxa ordinária, fundo de reserva, taxa extra, desconto do mandato...
-- Tabela filha: só é lida pela cobrança, que já é filtrada por condomínio.
CREATE TABLE cobranca_item (
    cobranca_id  uuid            NOT NULL REFERENCES cobranca (id) ON DELETE CASCADE,
    ordem        integer         NOT NULL,
    tipo         varchar(30)     NOT NULL CHECK (tipo IN ('TAXA_ORDINARIA', 'FUNDO_RESERVA', 'TAXA_EXTRA',
                                     'DESCONTO_MANDATO', 'RESERVA_DE_ESPACO', 'MULTA_DISCIPLINAR', 'CONSUMO', 'OUTRO')),
    descricao    varchar(200)    NOT NULL,
    valor        numeric(12, 2)  NOT NULL,                              -- negativo nos descontos
    PRIMARY KEY (cobranca_id, ordem)
);
