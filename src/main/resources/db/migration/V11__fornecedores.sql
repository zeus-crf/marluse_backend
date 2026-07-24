-- Fornecedores de produto (N:N). Campo opcional: nenhum backfill,
-- produtos existentes simplesmente ficam sem fornecedor.

CREATE TABLE fornecedores (
    id         VARCHAR(36)  NOT NULL,
    nome       VARCHAR(120) NOT NULL,
    ativo      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at DATETIME     NULL,
    updated_at DATETIME     NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_fornecedor_nome UNIQUE (nome)
);

CREATE TABLE produto_fornecedores (
    produto_id    VARCHAR(36) NOT NULL,
    fornecedor_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (produto_id, fornecedor_id),
    CONSTRAINT fk_pf_produto    FOREIGN KEY (produto_id)    REFERENCES produtos (id),
    CONSTRAINT fk_pf_fornecedor FOREIGN KEY (fornecedor_id) REFERENCES fornecedores (id)
);
