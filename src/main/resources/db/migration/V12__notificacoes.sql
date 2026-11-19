-- Etapa 18: notificações no app, por e-mail e por push.

-- Eventos entre módulos guardados pelo Spring Modulith (a "fila" no Postgres): se o envio de uma notificação falhar ou
-- o servidor cair, o evento continua aqui e é reprocessado. Formato definido pelo spring-modulith-events-jpa.
CREATE TABLE event_publication (
    id                      uuid         PRIMARY KEY,
    listener_id             text         NOT NULL,
    event_type              text         NOT NULL,
    serialized_event        text         NOT NULL,
    publication_date        timestamptz  NOT NULL,
    completion_date         timestamptz,
    status                  text,
    completion_attempts     integer      NOT NULL DEFAULT 0,
    last_resubmission_date  timestamptz
);
CREATE INDEX ix_event_publication_pendentes ON event_publication (completion_date, publication_date);

CREATE TABLE event_publication_archive (
    id                      uuid         PRIMARY KEY,
    listener_id             text         NOT NULL,
    event_type              text         NOT NULL,
    serialized_event        text         NOT NULL,
    publication_date        timestamptz  NOT NULL,
    completion_date         timestamptz,
    status                  text,
    completion_attempts     integer      NOT NULL DEFAULT 0,
    last_resubmission_date  timestamptz
);

-- Uma notificação para uma pessoa (login), num condomínio: é o que aparece no sino do app.
CREATE TABLE notificacao (
    id          uuid          PRIMARY KEY DEFAULT uuidv7(),
    tenant_id   uuid          NOT NULL REFERENCES condominio (id),
    usuario_id  uuid          NOT NULL REFERENCES usuario (id),
    grupo       varchar(20)   NOT NULL CHECK (grupo IN ('COBRANCAS', 'RESERVAS', 'ASSINATURA', 'DOCUMENTOS')),
    chave       varchar(150)  NOT NULL,          -- o mesmo acontecimento não gera duas notificações para a mesma pessoa
    titulo      varchar(150)  NOT NULL,
    texto       varchar(1000) NOT NULL,
    link        varchar(300),                    -- tela do app (ex.: /debitos)
    criado_em   timestamptz   NOT NULL DEFAULT now(),
    lido_em     timestamptz,
    UNIQUE (usuario_id, chave)
);
CREATE INDEX ix_notificacao_tenant_usuario ON notificacao (tenant_id, usuario_id, criado_em DESC);

-- A entrega de uma notificação por e-mail ou push, com as novas tentativas quando falha.
CREATE TABLE entrega_de_notificacao (
    id                   uuid          PRIMARY KEY DEFAULT uuidv7(),
    tenant_id            uuid          NOT NULL REFERENCES condominio (id),
    notificacao_id       uuid          NOT NULL REFERENCES notificacao (id) ON DELETE CASCADE,
    canal                varchar(10)   NOT NULL CHECK (canal IN ('EMAIL', 'PUSH')),
    situacao             varchar(15)   NOT NULL CHECK (situacao IN ('PENDENTE', 'ENVIADA', 'FALHOU', 'DESCARTADA')),
    tentativas           integer       NOT NULL DEFAULT 0,
    proxima_tentativa_em timestamptz   NOT NULL,
    erro                 varchar(500),
    enviada_em           timestamptz,
    UNIQUE (notificacao_id, canal)
);
CREATE INDEX ix_entrega_pendente ON entrega_de_notificacao (proxima_tentativa_em) WHERE situacao = 'PENDENTE';

-- O que cada pessoa quer receber por e-mail e por push (vale em todos os condomínios dela). Sem linha: recebe.
-- No app (o sino), a notificação sempre aparece.
CREATE TABLE preferencia_de_notificacao (
    usuario_id  uuid         NOT NULL REFERENCES usuario (id),
    grupo       varchar(20)  NOT NULL CHECK (grupo IN ('COBRANCAS', 'RESERVAS', 'ASSINATURA', 'DOCUMENTOS')),
    canal       varchar(10)  NOT NULL CHECK (canal IN ('EMAIL', 'PUSH')),
    ativo       boolean      NOT NULL,
    PRIMARY KEY (usuario_id, grupo, canal)
);

-- Os aparelhos (navegador ou app instalado) em que a pessoa ligou o push.
CREATE TABLE inscricao_push (
    id          uuid          PRIMARY KEY DEFAULT uuidv7(),
    usuario_id  uuid          NOT NULL REFERENCES usuario (id),
    endpoint    varchar(1000) NOT NULL UNIQUE,
    p256dh      varchar(200)  NOT NULL,
    auth        varchar(100)  NOT NULL,
    criada_em   timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX ix_inscricao_push_usuario ON inscricao_push (usuario_id);

-- O par de chaves VAPID do Web Push, gerado no primeiro uso. A chave privada fica cifrada pelo Cofre.
CREATE TABLE chaves_do_push (
    id               integer  PRIMARY KEY CHECK (id = 1),
    chave_publica    varchar(200) NOT NULL,
    chave_privada    text         NOT NULL,
    criada_em        timestamptz  NOT NULL DEFAULT now()
);
