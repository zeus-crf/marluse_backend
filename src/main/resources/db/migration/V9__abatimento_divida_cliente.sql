ALTER TABLE lancamento_financeiros
    ADD COLUMN valor_pago DECIMAL(10,2) NOT NULL DEFAULT 0;

CREATE TABLE pagamentos_cliente (
    id            VARCHAR(36)  NOT NULL,
    cliente_id    VARCHAR(36)  NOT NULL,
    valor         DECIMAL(10,2) NOT NULL,
    data          DATE          NOT NULL,
    observacao    VARCHAR(255)  NULL,
    estornado     BOOLEAN       NOT NULL DEFAULT FALSE,
    estornado_em  DATE          NULL,
    created_at    DATETIME      NULL,
    updated_at    DATETIME      NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_pagcliente_cliente FOREIGN KEY (cliente_id) REFERENCES clientes (id)
);

CREATE TABLE abatimento_parcela (
    id                   VARCHAR(36)  NOT NULL,
    pagamento_cliente_id VARCHAR(36)  NOT NULL,
    lancamento_id        VARCHAR(36)  NOT NULL,
    valor                DECIMAL(10,2) NOT NULL,
    created_at           DATETIME      NULL,
    updated_at           DATETIME      NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_abat_pagamento  FOREIGN KEY (pagamento_cliente_id) REFERENCES pagamentos_cliente (id),
    CONSTRAINT fk_abat_lancamento FOREIGN KEY (lancamento_id) REFERENCES lancamento_financeiros (id)
);