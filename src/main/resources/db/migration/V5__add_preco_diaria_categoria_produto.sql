ALTER TABLE produtos
    ADD COLUMN preco_diaria DECIMAL(10, 2) NULL,
    ADD COLUMN categoria VARCHAR(30) NULL;


UPDATE produtos SET preco_diaria = preco WHERE preco_diaria IS NULL;
UPDATE produtos SET categoria = 'OUTROS' WHERE categoria IS NULL;

ALTER TABLE produtos
    MODIFY COLUMN preco_diaria DECIMAL(10,2) NOT NULL,
    MODIFY COLUMN categoria    VARCHAR(30)   NOT NULL;