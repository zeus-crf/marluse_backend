ALTER TABLE itens_pedido
    ADD COLUMN baixar_estoque        TINYINT(1) NOT NULL DEFAULT 1,
    ADD COLUMN permitir_sem_estoque  TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN estoque_descontado    TINYINT(1) NOT NULL DEFAULT 0;

ALTER TABLE itens_locacao
    ADD COLUMN baixar_estoque        TINYINT(1) NOT NULL DEFAULT 1,
    ADD COLUMN permitir_sem_estoque  TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN estoque_descontado    TINYINT(1) NOT NULL DEFAULT 0;