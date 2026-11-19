-- Etapa 16: o Condomix cobra a assinatura dos condomínios pelo gateway.

-- Teste grátis com prazo: ao fim, a assinatura vira ativa sozinha e começa a ser faturada.
-- Vazio = teste sem prazo (o administrador ativa à mão).
ALTER TABLE assinatura ADD COLUMN fim_do_teste date;
UPDATE assinatura SET fim_do_teste = inicio + 30 WHERE status = 'TESTE';
ALTER TABLE assinatura ADD CONSTRAINT ck_assinatura_fim_do_teste CHECK (fim_do_teste IS NULL OR status = 'TESTE');

-- Por que o condomínio está suspenso. Só a suspensão por falta de pagamento volta sozinha quando a fatura é paga.
ALTER TABLE condominio ADD COLUMN motivo_suspensao varchar(30)
    CHECK (motivo_suspensao IN ('MANUAL', 'FALTA_DE_PAGAMENTO'));
UPDATE condominio SET motivo_suspensao = 'MANUAL' WHERE NOT ativo;
ALTER TABLE condominio ADD CONSTRAINT ck_condominio_motivo_suspensao CHECK ((motivo_suspensao IS NULL) = ativo);

-- Novos acontecimentos no histórico da assinatura.
ALTER TABLE evento_assinatura ALTER COLUMN tipo TYPE varchar(40);
ALTER TABLE evento_assinatura DROP CONSTRAINT evento_assinatura_tipo_check;
ALTER TABLE evento_assinatura ADD CONSTRAINT evento_assinatura_tipo_check CHECK (tipo IN (
    'CLIENTE_NOVO', 'TROCA_DE_PLANO', 'MUDANCA_DE_SITUACAO', 'SUSPENSAO', 'REATIVACAO',
    'PRAZO_DO_TESTE', 'FIM_DO_TESTE', 'SUSPENSAO_POR_FALTA_DE_PAGAMENTO', 'REATIVACAO_APOS_PAGAMENTO'));
ALTER TABLE evento_assinatura ADD COLUMN fim_do_teste date;

-- A conta do Condomix no gateway, que recebe as assinaturas. Uma só (dado da plataforma, sem tenant_id).
CREATE TABLE conta_de_recebimento (
    id                uuid          PRIMARY KEY DEFAULT uuidv7(),
    unica             boolean       NOT NULL DEFAULT true UNIQUE CHECK (unica),
    gateway           varchar(20)   NOT NULL CHECK (gateway IN ('ASAAS', 'SIMULADO')),
    ambiente          varchar(20)   NOT NULL CHECK (ambiente IN ('SANDBOX', 'PRODUCAO')),
    chave_cifrada     text          NOT NULL,
    token_aviso_hash  varchar(64)   NOT NULL,
    ativa             boolean       NOT NULL DEFAULT true,
    configurada_em    timestamptz   NOT NULL,
    configurada_por   uuid          REFERENCES usuario (id)
);

-- Fatura da assinatura: um período (mês, semestre ou ano) do plano, pago antes de começar.
CREATE TABLE fatura (
    id                uuid           PRIMARY KEY DEFAULT uuidv7(),
    tenant_id         uuid           NOT NULL REFERENCES condominio (id),
    plano_id          uuid           NOT NULL REFERENCES plano (id),
    nome_do_plano     varchar(100)   NOT NULL,          -- como o plano se chamava na fatura
    periodicidade     varchar(20)    NOT NULL CHECK (periodicidade IN ('MENSAL', 'SEMESTRAL', 'ANUAL')),
    periodo_inicio    date           NOT NULL,
    periodo_fim       date           NOT NULL,
    valor             numeric(10, 2) NOT NULL CHECK (valor > 0),
    vencimento        date           NOT NULL,
    situacao          varchar(20)    NOT NULL
        CHECK (situacao IN ('A_EMITIR', 'EMITIDA', 'FALHOU', 'PAGA', 'A_CANCELAR', 'CANCELADA')),
    gateway           varchar(20)    CHECK (gateway IN ('ASAAS', 'SIMULADO')),
    id_no_gateway     varchar(100),
    url               text,
    linha_digitavel   varchar(100),
    pix_copia_e_cola  text,
    pix_qr_code       text,
    erro              text,
    tentativas        integer        NOT NULL DEFAULT 0,
    pago_em           date,
    valor_pago        numeric(10, 2),
    forma_pagamento   varchar(20)    CHECK (forma_pagamento IN ('BOLETO', 'PIX', 'OUTRA')),
    tarifa            numeric(10, 2),
    cancelada_em      timestamptz,
    cancelada_por     uuid           REFERENCES usuario (id),
    criado_em         timestamptz    NOT NULL DEFAULT now(),
    atualizado_em     timestamptz,
    CHECK (periodo_fim > periodo_inicio),
    CHECK ((situacao = 'PAGA') = (pago_em IS NOT NULL))
);
CREATE INDEX ix_fatura_tenant ON fatura (tenant_id, periodo_inicio DESC);
CREATE INDEX ix_fatura_a_processar ON fatura (situacao) WHERE situacao IN ('A_EMITIR', 'A_CANCELAR', 'EMITIDA');
CREATE UNIQUE INDEX ux_fatura_no_gateway ON fatura (gateway, id_no_gateway) WHERE id_no_gateway IS NOT NULL;
-- O mesmo período não é faturado duas vezes (uma cancelada pode ser refeita).
CREATE UNIQUE INDEX ux_fatura_periodo ON fatura (tenant_id, periodo_inicio) WHERE situacao <> 'CANCELADA';
