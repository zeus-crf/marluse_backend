-- Idempotente (MySQL não tem ADD COLUMN IF NOT EXISTS): checa information_schema antes de cada DDL.

-- produtos.tipo
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'produtos' AND COLUMN_NAME = 'tipo');
SET @ddl := IF(@exists = 0,
    'ALTER TABLE produtos ADD COLUMN tipo VARCHAR(20) NULL', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- produtos.preco_semanal
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'produtos' AND COLUMN_NAME = 'preco_semanal');
SET @ddl := IF(@exists = 0,
    'ALTER TABLE produtos ADD COLUMN preco_semanal DECIMAL(10,2) NULL', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- produtos.preco_mensal
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'produtos' AND COLUMN_NAME = 'preco_mensal');
SET @ddl := IF(@exists = 0,
    'ALTER TABLE produtos ADD COLUMN preco_mensal DECIMAL(10,2) NULL', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- itens_locacao.unidade_cobranca
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'itens_locacao' AND COLUMN_NAME = 'unidade_cobranca');
SET @ddl := IF(@exists = 0,
    'ALTER TABLE itens_locacao ADD COLUMN unidade_cobranca VARCHAR(10) NULL', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- Backfill do tipo (idempotente: só afeta linhas com tipo NULL).
-- Marca LOCACAO apenas por categoria = 'LOCACAO' — único sinal confiável no dado
-- legado. (Heurísticas por diária ou por uso em itens_locacao foram descartadas:
-- o fluxo antigo permitia alugar qualquer produto, então marcavam vendas por engano.)
-- Produtos de locação sem essa categoria devem ser re-marcados no modal após o deploy.
UPDATE produtos SET tipo = 'LOCACAO' WHERE tipo IS NULL AND categoria = 'LOCACAO';

-- O restante é venda.
UPDATE produtos SET tipo = 'VENDA' WHERE tipo IS NULL;

UPDATE itens_locacao SET unidade_cobranca = 'DIARIA' WHERE unidade_cobranca IS NULL;

-- NOT NULL após backfill
ALTER TABLE produtos           MODIFY COLUMN tipo             VARCHAR(20) NOT NULL;
ALTER TABLE itens_locacao      MODIFY COLUMN unidade_cobranca VARCHAR(10) NOT NULL;
