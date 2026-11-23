-- Expansão sem atualização de notas. O histórico e as constraints existentes
-- continuam intactos. As colunas novas são opcionais para versões antigas.
SET LOCAL lock_timeout = '5s';

ALTER TABLE nota_entrada
    ADD COLUMN valor_produtos_micros NUMERIC(19,0) CHECK (valor_produtos_micros >= 0),
    ADD COLUMN valor_frete_micros NUMERIC(19,0) CHECK (valor_frete_micros >= 0),
    ADD COLUMN valor_seguro_micros NUMERIC(19,0) CHECK (valor_seguro_micros >= 0),
    ADD COLUMN outras_despesas_micros NUMERIC(19,0) CHECK (outras_despesas_micros >= 0),
    ADD COLUMN valor_desconto_micros NUMERIC(19,0) CHECK (valor_desconto_micros >= 0),
    ADD COLUMN valor_total_micros NUMERIC(19,0) CHECK (valor_total_micros >= 0);

ALTER TABLE item_nota_entrada
    ADD COLUMN quantidade_micros NUMERIC(18,0) CHECK (quantidade_micros > 0),
    ADD COLUMN valor_unitario_micros NUMERIC(19,0) CHECK (valor_unitario_micros >= 0),
    ADD COLUMN valor_desconto_micros NUMERIC(19,0) CHECK (valor_desconto_micros >= 0),
    ADD COLUMN percentual_desconto_micros NUMERIC(9,0) CHECK (percentual_desconto_micros BETWEEN 0 AND 100000000),
    ADD COLUMN valor_total_micros NUMERIC(19,0) CHECK (valor_total_micros >= 0),
    ADD COLUMN rateio_frete_micros NUMERIC(19,0) CHECK (rateio_frete_micros >= 0),
    ADD COLUMN rateio_seguro_micros NUMERIC(19,0) CHECK (rateio_seguro_micros >= 0),
    ADD COLUMN rateio_outras_micros NUMERIC(19,0) CHECK (rateio_outras_micros >= 0),
    ADD COLUMN custo_total_micros NUMERIC(19,0) CHECK (custo_total_micros >= 0),
    ADD COLUMN custo_preco_final_micros NUMERIC(19,0) CHECK (custo_preco_final_micros >= 0),
    ADD COLUMN saldo_estoque_anterior_micros NUMERIC(18,0) CHECK (saldo_estoque_anterior_micros >= 0),
    ADD COLUMN custo_medio_anterior_micros NUMERIC(19,0) CHECK (custo_medio_anterior_micros >= 0),
    ADD COLUMN custo_medio_posterior_micros NUMERIC(19,0) CHECK (custo_medio_posterior_micros >= 0);

ALTER TABLE parcela_nota_entrada
    ADD COLUMN valor_micros NUMERIC(19,0) CHECK (valor_micros > 0);
