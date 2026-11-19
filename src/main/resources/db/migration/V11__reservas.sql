-- Etapa 17: reservas dos espaços comuns.

-- Espaço comum (salão de festas, churrasqueira, quadra...) com as regras de reserva.
CREATE TABLE espaco (
    id                        uuid           PRIMARY KEY DEFAULT uuidv7(),
    tenant_id                 uuid           NOT NULL REFERENCES condominio (id),
    nome                      varchar(100)   NOT NULL,
    descricao                 text,
    capacidade                integer        CHECK (capacidade > 0),
    valor_aluguel             numeric(10, 2) NOT NULL DEFAULT 0 CHECK (valor_aluguel >= 0),
    antecedencia_minima_dias  integer        NOT NULL DEFAULT 0 CHECK (antecedencia_minima_dias >= 0),
    antecedencia_maxima_dias  integer        NOT NULL DEFAULT 90,
    limite_por_mes            integer        CHECK (limite_por_mes > 0),          -- vazio: sem limite
    bloqueia_inadimplente     boolean        NOT NULL DEFAULT true,
    dias_para_cancelar        integer        NOT NULL DEFAULT 0 CHECK (dias_para_cancelar >= 0),
    ativo                     boolean        NOT NULL DEFAULT true,
    criado_em                 timestamptz    NOT NULL DEFAULT now(),
    atualizado_em             timestamptz,
    CHECK (antecedencia_maxima_dias >= antecedencia_minima_dias),
    UNIQUE (tenant_id, nome)
);
CREATE INDEX ix_espaco_tenant ON espaco (tenant_id);

-- Os horários que podem ser reservados em cada espaço (ex.: "Dia inteiro", ou "Almoço" e "Noite"). Não se sobrepõem.
-- Horas como texto "HH:MM" (hora do condomínio): o Hibernate converteria um "time" para UTC.
CREATE TABLE periodo_do_espaco (
    espaco_id  uuid         NOT NULL REFERENCES espaco (id) ON DELETE CASCADE,
    nome       varchar(50)  NOT NULL,
    inicio     varchar(5)   NOT NULL CHECK (inicio ~ '^([01][0-9]|2[0-3]):[0-5][0-9]$'),
    fim        varchar(5)   NOT NULL CHECK (fim ~ '^([01][0-9]|2[0-3]):[0-5][0-9]$'),
    CHECK (fim > inicio),
    PRIMARY KEY (espaco_id, nome)
);

-- A reserva de um período de um espaço, num dia, para uma unidade.
-- Período, horário e valor ficam gravados como eram na hora da reserva.
CREATE TABLE reserva (
    id                    uuid           PRIMARY KEY DEFAULT uuidv7(),
    tenant_id             uuid           NOT NULL REFERENCES condominio (id),
    espaco_id             uuid           NOT NULL REFERENCES espaco (id),
    unidade_id            uuid           NOT NULL REFERENCES unidade (id),
    data                  date           NOT NULL,
    periodo               varchar(50)    NOT NULL,
    inicio                varchar(5)     NOT NULL,                     -- "HH:MM", como em periodo_do_espaco
    fim                   varchar(5)     NOT NULL,
    situacao              varchar(20)    NOT NULL CHECK (situacao IN ('CONFIRMADA', 'CANCELADA')),
    valor                 numeric(10, 2) NOT NULL DEFAULT 0 CHECK (valor >= 0),
    cobranca_id           uuid           REFERENCES cobranca (id),   -- o aluguel, quando o espaço cobra
    observacao            text,
    reservada_por         uuid           REFERENCES usuario (id),
    criado_em             timestamptz    NOT NULL DEFAULT now(),
    cancelada_em          timestamptz,
    cancelada_por         uuid           REFERENCES usuario (id),
    motivo_cancelamento   text,
    aluguel_mantido       boolean        NOT NULL DEFAULT false,      -- cancelou fora do prazo: o aluguel continua devido
    CHECK (fim > inicio),
    CHECK ((situacao = 'CANCELADA') = (cancelada_em IS NOT NULL))
);
CREATE INDEX ix_reserva_tenant_data ON reserva (tenant_id, data);
CREATE INDEX ix_reserva_unidade ON reserva (unidade_id, data);
-- O mesmo horário de um espaço não é reservado duas vezes (os períodos de um espaço não se sobrepõem).
CREATE UNIQUE INDEX ux_reserva_horario ON reserva (espaco_id, data, inicio) WHERE situacao = 'CONFIRMADA';
