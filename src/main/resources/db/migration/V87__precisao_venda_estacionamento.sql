-- Lote, reserva e pedido de estacionamento usam a mesma unidade de armazenamento.
-- Os snapshots já gravados ficam intactos. Os defaults antigos só atendem ao
-- esquema legado; o novo pedido usa exclusivamente os campos em milionésimos.
SET LOCAL lock_timeout = '5s';

ALTER TABLE lote_preco
    ADD COLUMN preco_micros NUMERIC(23,0) CHECK (preco_micros >= 0),
    ALTER COLUMN preco SET DEFAULT 0;

ALTER TABLE reserva_inventario
    ADD COLUMN preco_unitario_micros NUMERIC(23,0) CHECK (preco_unitario_micros >= 0),
    ALTER COLUMN preco_unitario SET DEFAULT 0;

ALTER TABLE pedido
    ADD COLUMN subtotal_micros NUMERIC(23,0),
    ADD COLUMN desconto_micros NUMERIC(23,0),
    ADD COLUMN taxa_micros NUMERIC(23,0),
    ADD COLUMN total_micros NUMERIC(23,0),
    ALTER COLUMN subtotal SET DEFAULT 0,
    ALTER COLUMN total SET DEFAULT 0,
    ADD CONSTRAINT ck_pedido_totais_micros CHECK (
        num_nonnulls(subtotal_micros, desconto_micros, taxa_micros, total_micros) = 0 OR (
            num_nonnulls(subtotal_micros, desconto_micros, taxa_micros, total_micros) = 4
            AND subtotal_micros >= 0 AND desconto_micros >= 0 AND taxa_micros >= 0
            AND total_micros >= 0 AND total_micros = subtotal_micros - desconto_micros + taxa_micros
        )
    );

ALTER TABLE item_pedido
    ADD COLUMN preco_unitario_micros NUMERIC(23,0),
    ADD COLUMN total_micros NUMERIC(23,0),
    ALTER COLUMN preco_unitario SET DEFAULT 0,
    ALTER COLUMN total SET DEFAULT 0,
    ADD CONSTRAINT ck_item_pedido_total_micros CHECK (
        num_nonnulls(preco_unitario_micros, total_micros) = 0 OR (
            num_nonnulls(preco_unitario_micros, total_micros) = 2
            AND preco_unitario_micros >= 0 AND total_micros = preco_unitario_micros * quantidade
        )
    );
