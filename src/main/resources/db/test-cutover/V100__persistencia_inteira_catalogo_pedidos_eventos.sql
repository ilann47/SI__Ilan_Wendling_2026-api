-- Troca a representação sem recompor preço, margem ou totais históricos.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

DO $$
BEGIN
    IF current_database() <> 'estacionamento_kaneko_fiscal_20261105_test'
        OR inet_server_addr() IS DISTINCT FROM inet '127.0.0.1' THEN
        RAISE EXCEPTION 'V100 restrita ao banco fiscal local de teste';
    END IF;
END $$;

-- O valor inteiro, quando presente, já é canônico na aplicação. Reaproveitar
-- a coluna original conserva NOT NULL, CHECKs e defaults significativos.
ALTER TABLE produto
    ALTER COLUMN valor_venda TYPE NUMERIC(19,0) USING coalesce(valor_venda_micros, kaneko_micros(valor_venda)),
    ALTER COLUMN percentual_lucro TYPE NUMERIC(27,0) USING kaneko_micros(percentual_lucro),
    DROP COLUMN valor_venda_micros;
ALTER TABLE produto RENAME COLUMN valor_venda TO valor_venda_micros;
ALTER TABLE produto RENAME COLUMN percentual_lucro TO percentual_lucro_micros;

ALTER TABLE produto_fornecedor
    ALTER COLUMN custo TYPE NUMERIC(19,0) USING kaneko_micros(custo);
ALTER TABLE produto_fornecedor RENAME COLUMN custo TO custo_micros;

ALTER TABLE lote_preco
    ALTER COLUMN preco TYPE NUMERIC(23,0) USING coalesce(preco_micros, kaneko_micros(preco)),
    ALTER COLUMN preco DROP DEFAULT,
    DROP COLUMN preco_micros;
ALTER TABLE lote_preco RENAME COLUMN preco TO preco_micros;

ALTER TABLE reserva_inventario
    ALTER COLUMN preco_unitario TYPE NUMERIC(23,0) USING coalesce(preco_unitario_micros, kaneko_micros(preco_unitario)),
    ALTER COLUMN preco_unitario DROP DEFAULT,
    DROP COLUMN preco_unitario_micros;
ALTER TABLE reserva_inventario RENAME COLUMN preco_unitario TO preco_unitario_micros;

ALTER TABLE pedido
    ALTER COLUMN subtotal TYPE NUMERIC(23,0) USING coalesce(subtotal_micros, kaneko_micros(subtotal)),
    ALTER COLUMN desconto TYPE NUMERIC(23,0) USING coalesce(desconto_micros, kaneko_micros(desconto)),
    ALTER COLUMN taxa TYPE NUMERIC(23,0) USING coalesce(taxa_micros, kaneko_micros(taxa)),
    ALTER COLUMN total TYPE NUMERIC(23,0) USING coalesce(total_micros, kaneko_micros(total)),
    ALTER COLUMN subtotal DROP DEFAULT,
    ALTER COLUMN total DROP DEFAULT,
    DROP COLUMN subtotal_micros,
    DROP COLUMN desconto_micros,
    DROP COLUMN taxa_micros,
    DROP COLUMN total_micros;
ALTER TABLE pedido RENAME COLUMN subtotal TO subtotal_micros;
ALTER TABLE pedido RENAME COLUMN desconto TO desconto_micros;
ALTER TABLE pedido RENAME COLUMN taxa TO taxa_micros;
ALTER TABLE pedido RENAME COLUMN total TO total_micros;

ALTER TABLE item_pedido
    ALTER COLUMN preco_unitario TYPE NUMERIC(23,0) USING coalesce(preco_unitario_micros, kaneko_micros(preco_unitario)),
    ALTER COLUMN total TYPE NUMERIC(23,0) USING coalesce(total_micros, kaneko_micros(total)),
    ALTER COLUMN preco_unitario DROP DEFAULT,
    ALTER COLUMN total DROP DEFAULT,
    DROP COLUMN preco_unitario_micros,
    DROP COLUMN total_micros;
ALTER TABLE item_pedido RENAME COLUMN preco_unitario TO preco_unitario_micros;
ALTER TABLE item_pedido RENAME COLUMN total TO total_micros;
