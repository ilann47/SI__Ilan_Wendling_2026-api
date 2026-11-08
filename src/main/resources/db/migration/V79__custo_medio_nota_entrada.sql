ALTER TABLE movimento_estoque
    ADD COLUMN saldo_total_anterior numeric(15,3),
    ADD COLUMN saldo_total_posterior numeric(15,3),
    ADD COLUMN custo_medio_anterior numeric(15,2),
    ADD COLUMN custo_medio_posterior numeric(15,2);

ALTER TABLE item_nota_entrada
    ADD COLUMN saldo_estoque_anterior numeric(15,3),
    ADD COLUMN custo_medio_anterior numeric(15,2),
    ADD COLUMN custo_medio_posterior numeric(15,2);

COMMENT ON COLUMN produto.custo IS
    'Custo medio ponderado vigente; saldos anteriores a V79 usam o custo cadastrado como marco inicial.';
