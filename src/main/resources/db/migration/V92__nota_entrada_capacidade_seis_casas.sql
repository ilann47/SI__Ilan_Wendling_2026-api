-- Amplia a capacidade dos campos de compatibilidade. Não reprocessa documentos.
SET LOCAL lock_timeout = '5s';

ALTER TABLE nota_entrada
    ALTER COLUMN valor_produtos TYPE NUMERIC(19,6),
    ALTER COLUMN valor_frete TYPE NUMERIC(19,6),
    ALTER COLUMN valor_seguro TYPE NUMERIC(19,6),
    ALTER COLUMN outras_despesas TYPE NUMERIC(19,6),
    ALTER COLUMN valor_desconto TYPE NUMERIC(19,6),
    ALTER COLUMN valor_total TYPE NUMERIC(19,6);

ALTER TABLE item_nota_entrada
    ALTER COLUMN quantidade TYPE NUMERIC(18,6),
    ALTER COLUMN valor_unitario TYPE NUMERIC(19,6),
    ALTER COLUMN valor_desconto TYPE NUMERIC(19,6),
    ALTER COLUMN percentual_desconto TYPE NUMERIC(9,6),
    ALTER COLUMN valor_total TYPE NUMERIC(19,6),
    ALTER COLUMN rateio_frete TYPE NUMERIC(19,6),
    ALTER COLUMN rateio_seguro TYPE NUMERIC(19,6),
    ALTER COLUMN rateio_outras TYPE NUMERIC(19,6),
    ALTER COLUMN custo_total TYPE NUMERIC(19,6),
    ALTER COLUMN custo_preco_final TYPE NUMERIC(19,6),
    ALTER COLUMN saldo_estoque_anterior TYPE NUMERIC(18,6),
    ALTER COLUMN custo_medio_anterior TYPE NUMERIC(19,6),
    ALTER COLUMN custo_medio_posterior TYPE NUMERIC(19,6);

ALTER TABLE parcela_nota_entrada ALTER COLUMN valor TYPE NUMERIC(19,6);
