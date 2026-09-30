ALTER TABLE ordem_compra
    ADD COLUMN numero_nota VARCHAR(60),
    ADD COLUMN serie_nota VARCHAR(20),
    ADD COLUMN modelo_nota VARCHAR(10);

UPDATE ordem_compra
SET numero_nota = numero,
    serie_nota = 'LEGADO',
    modelo_nota = 'LEGADO'
WHERE numero_nota IS NULL;

ALTER TABLE ordem_compra
    ALTER COLUMN numero_nota SET NOT NULL,
    ALTER COLUMN serie_nota SET NOT NULL,
    ALTER COLUMN modelo_nota SET NOT NULL;

ALTER TABLE ordem_compra
    ADD CONSTRAINT uk_ordem_compra_chave_fiscal
        UNIQUE (organizacao_id, fornecedor_id, numero_nota, serie_nota, modelo_nota);

CREATE INDEX idx_ordem_compra_chave_fiscal
    ON ordem_compra (organizacao_id, numero_nota, serie_nota, modelo_nota);
