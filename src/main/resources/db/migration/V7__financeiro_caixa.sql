-- =====================================================================
-- V7 - Financeiro do condomínio: contas, plano de contas, lançamentos (caixa) e balancete
-- =====================================================================

ALTER TABLE cobranca ADD CONSTRAINT uq_cobranca_id_tenant UNIQUE (id, tenant_id);

-- Onde o dinheiro do condomínio fica: conta corrente, poupança, aplicação do fundo de reserva, caixa físico.
CREATE TABLE conta_financeira (
    id                    uuid            PRIMARY KEY DEFAULT uuidv7(),
    tenant_id             uuid            NOT NULL REFERENCES condominio (id),
    nome                  varchar(80)     NOT NULL,
    tipo                  varchar(20)     NOT NULL CHECK (tipo IN ('CONTA_CORRENTE', 'POUPANCA', 'APLICACAO', 'CAIXA_FISICO', 'GATEWAY')),
    banco                 varchar(80),
    agencia               varchar(20),
    numero                varchar(30),
    saldo_inicial         numeric(14, 2)  NOT NULL,
    data_saldo_inicial    date            NOT NULL,                  -- lançamentos só a partir desta data
    recebimento_padrao    boolean         NOT NULL DEFAULT false,    -- onde caem os pagamentos das cobranças
    ativa                 boolean         NOT NULL DEFAULT true,
    criado_em             timestamptz     NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, nome),
    UNIQUE (id, tenant_id)
);
CREATE UNIQUE INDEX ux_conta_recebimento_padrao ON conta_financeira (tenant_id) WHERE recebimento_padrao;

-- Plano de contas: as categorias de receita e de despesa. As do sistema têm código (usadas nos lançamentos automáticos).
CREATE TABLE categoria_financeira (
    id         uuid          PRIMARY KEY DEFAULT uuidv7(),
    tenant_id  uuid          NOT NULL REFERENCES condominio (id),
    codigo     varchar(40),
    nome       varchar(80)   NOT NULL,
    natureza   varchar(10)   NOT NULL CHECK (natureza IN ('RECEITA', 'DESPESA')),
    ativa      boolean       NOT NULL DEFAULT true,
    UNIQUE (tenant_id, nome),
    UNIQUE (tenant_id, codigo),
    UNIQUE (id, tenant_id)
);

-- Cada entrada ou saída de dinheiro de uma conta.
CREATE TABLE lancamento (
    id                   uuid            PRIMARY KEY DEFAULT uuidv7(),
    tenant_id            uuid            NOT NULL REFERENCES condominio (id),
    conta_id             uuid            NOT NULL,
    categoria_id         uuid,
    tipo                 varchar(30)     NOT NULL CHECK (tipo IN ('RECEITA', 'DESPESA', 'TRANSFERENCIA_ENTRADA', 'TRANSFERENCIA_SAIDA')),
    origem               varchar(20)     NOT NULL CHECK (origem IN ('MANUAL', 'COBRANCA', 'TRANSFERENCIA')),
    data                 date            NOT NULL,
    descricao            varchar(200)    NOT NULL,
    valor                numeric(14, 2)  NOT NULL CHECK (valor > 0),
    favorecido           varchar(150),                               -- fornecedor, prestador, quem pagou...
    cobranca_id          uuid,                                       -- lançamentos gerados pelo pagamento de uma cobrança
    transferencia_id     uuid,                                       -- liga a saída e a entrada de uma transferência
    cancelado_em         timestamptz,
    motivo_cancelamento  varchar(500),
    criado_por           uuid            REFERENCES usuario (id),
    criado_em            timestamptz     NOT NULL DEFAULT now(),

    FOREIGN KEY (conta_id, tenant_id)     REFERENCES conta_financeira (id, tenant_id),
    FOREIGN KEY (categoria_id, tenant_id) REFERENCES categoria_financeira (id, tenant_id),
    FOREIGN KEY (cobranca_id, tenant_id)  REFERENCES cobranca (id, tenant_id),
    CHECK ((tipo IN ('RECEITA', 'DESPESA')) = (categoria_id IS NOT NULL)),
    CHECK ((origem = 'COBRANCA') = (cobranca_id IS NOT NULL)),
    CHECK ((origem = 'TRANSFERENCIA') = (transferencia_id IS NOT NULL))
);
CREATE INDEX ix_lancamento_tenant_data ON lancamento (tenant_id, data);
CREATE INDEX ix_lancamento_conta_data ON lancamento (conta_id, data);
CREATE INDEX ix_lancamento_cobranca ON lancamento (cobranca_id);
