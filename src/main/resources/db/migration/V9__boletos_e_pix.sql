-- =====================================================================
-- V9 - Boletos e Pix pelo gateway de pagamentos (Asaas), 2ª via e baixa automática
-- =====================================================================

-- A conta do condomínio no gateway. Cada condomínio tem a sua (o dinheiro cai direto nela).
-- A chave de API fica cifrada (ver Cofre); o token do aviso (webhook) fica só como impressão digital.
CREATE TABLE conta_gateway (
    id                    uuid          PRIMARY KEY DEFAULT uuidv7(),
    tenant_id             uuid          NOT NULL UNIQUE REFERENCES condominio (id),
    gateway               varchar(20)   NOT NULL CHECK (gateway IN ('ASAAS', 'SIMULADO')),
    ambiente              varchar(20)   NOT NULL CHECK (ambiente IN ('SANDBOX', 'PRODUCAO')),
    chave_cifrada         varchar(1000) NOT NULL,
    token_aviso_hash      varchar(64)   NOT NULL,
    ativa                 boolean       NOT NULL DEFAULT true,
    configurada_em        timestamptz   NOT NULL,
    configurada_por       uuid          REFERENCES usuario (id)
);

-- O boleto (com Pix) de uma cobrança no gateway. Uma cobrança pode ter vários ao longo do tempo
-- (ex.: cancelado e emitido de novo), mas só um que não esteja cancelado.
CREATE TABLE boleto (
    id                  uuid            PRIMARY KEY DEFAULT uuidv7(),
    tenant_id           uuid            NOT NULL REFERENCES condominio (id),
    cobranca_id         uuid            NOT NULL,
    gateway             varchar(20)     NOT NULL CHECK (gateway IN ('ASAAS', 'SIMULADO')),
    situacao            varchar(30)     NOT NULL CHECK (situacao IN ('PENDENTE', 'EMITIDO', 'FALHOU', 'PAGO', 'A_CANCELAR', 'CANCELADO')),
    id_no_gateway       varchar(100),
    valor               numeric(12, 2),
    vencimento          date,
    url                 varchar(500),                 -- o boleto em PDF / página de pagamento
    linha_digitavel     varchar(100),
    pix_copia_e_cola    varchar(1000),
    pix_qr_code         text,                         -- imagem PNG em Base64
    erro                varchar(500),                 -- por que a emissão falhou (mostrado ao síndico)
    tentativas          integer         NOT NULL DEFAULT 0,
    tarifa              numeric(12, 2),               -- o que o gateway cobrou, quando pago
    criado_em           timestamptz     NOT NULL DEFAULT now(),
    atualizado_em       timestamptz     NOT NULL DEFAULT now(),

    FOREIGN KEY (cobranca_id, tenant_id) REFERENCES cobranca (id, tenant_id),
    CHECK (situacao IN ('PENDENTE', 'FALHOU') OR id_no_gateway IS NOT NULL OR situacao = 'CANCELADO')
);
CREATE UNIQUE INDEX ux_boleto_cobranca_vigente ON boleto (cobranca_id) WHERE situacao <> 'CANCELADO';
CREATE UNIQUE INDEX ux_boleto_id_no_gateway ON boleto (tenant_id, gateway, id_no_gateway);
CREATE INDEX ix_boleto_tenant_cobranca ON boleto (tenant_id, cobranca_id);
-- O processador busca os pendentes de todos os condomínios.
CREATE INDEX ix_boleto_a_processar ON boleto (situacao) WHERE situacao IN ('PENDENTE', 'A_CANCELAR');
