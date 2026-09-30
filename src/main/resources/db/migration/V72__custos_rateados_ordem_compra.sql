ALTER TABLE ordem_compra
    ADD COLUMN valor_seguro NUMERIC(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN outras_despesas NUMERIC(15,2) NOT NULL DEFAULT 0;

ALTER TABLE ordem_compra DROP CONSTRAINT ck_ordem_compra_valores;
ALTER TABLE ordem_compra ADD CONSTRAINT ck_ordem_compra_valores CHECK (
    subtotal >= 0
    AND valor_frete >= 0
    AND valor_seguro >= 0
    AND outras_despesas >= 0
    AND valor_desconto >= 0
    AND valor_desconto <= subtotal + valor_frete + valor_seguro + outras_despesas
    AND valor_total = subtotal + valor_frete + valor_seguro
        + outras_despesas - valor_desconto
);

ALTER TABLE item_ordem_compra
    ADD COLUMN rateio_frete NUMERIC(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN rateio_seguro NUMERIC(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN rateio_outras_despesas NUMERIC(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN custo_total NUMERIC(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN custo_unitario_final NUMERIC(15,2) NOT NULL DEFAULT 0;

UPDATE item_ordem_compra
SET custo_total = valor_total,
    custo_unitario_final = ROUND(valor_total / quantidade_pedida, 2);

ALTER TABLE item_ordem_compra ADD CONSTRAINT ck_item_ordem_compra_rateios CHECK (
    rateio_frete >= 0
    AND rateio_seguro >= 0
    AND rateio_outras_despesas >= 0
    AND custo_total = valor_total + rateio_frete + rateio_seguro
        + rateio_outras_despesas
    AND custo_unitario_final >= 0
);
