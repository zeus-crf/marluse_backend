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
-- Marca LOCACAO por dois sinais confiáveis, sem falso positivo:
--   1) categoria = 'LOCACAO' (marcação explícita), OU
--   2) produto já usado em alguma locação (itens_locacao) — se já foi alugado, é
--      locação de verdade, e isso evita que equipamentos reais sumam do dropdown.
-- (Heurística por preco_diaria foi descartada: marcava produtos de venda por engano.)
UPDATE produtos p
LEFT JOIN itens_locacao il ON il.produto_id = p.id
SET p.tipo = 'LOCACAO'
WHERE p.tipo IS NULL
  AND (
        p.categoria = 'LOCACAO'
     OR il.produto_id IS NOT NULL
  );

-- O restante é venda.
UPDATE produtos SET tipo = 'VENDA' WHERE tipo IS NULL;

UPDATE itens_locacao SET unidade_cobranca = 'DIARIA' WHERE unidade_cobranca IS NULL;

-- NOT NULL após backfill
ALTER TABLE produtos           MODIFY COLUMN tipo             VARCHAR(20) NOT NULL;
ALTER TABLE itens_locacao      MODIFY COLUMN unidade_cobranca VARCHAR(10) NOT NULL;
