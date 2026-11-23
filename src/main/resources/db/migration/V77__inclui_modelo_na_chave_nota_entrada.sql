DROP INDEX uq_nota_entrada_tenant;
DROP INDEX uq_nota_entrada_legado;

CREATE UNIQUE INDEX uq_nota_entrada_tenant
    ON nota_entrada (organizacao_id, numero, serie, modelo, fornecedor_id)
    WHERE organizacao_id IS NOT NULL;

CREATE UNIQUE INDEX uq_nota_entrada_legado
    ON nota_entrada (numero, serie, modelo, fornecedor_id)
    WHERE organizacao_id IS NULL;
